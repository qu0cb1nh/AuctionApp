package net.auctionapp.server.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.utils.ConfigUtil;
import net.auctionapp.server.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

public final class CloudinaryImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryImageService.class);
    private static final String DEFAULT_IMAGE_CONTENT_TYPE = "image/jpeg";
    private static final String CLOUDINARY_FOLDER = "auction-items";
    private static CloudinaryImageService instance;

    private final Cloudinary cloudinary;

    private CloudinaryImageService() {
        String cloudinaryUrl = ConfigUtil.getCloudinaryUrl();
        if (cloudinaryUrl.isBlank()) {
            cloudinary = null;
            return;
        }
        cloudinary = new Cloudinary(cloudinaryUrl);
    }

    public static synchronized CloudinaryImageService getInstance() {
        if (instance == null) {
            instance = new CloudinaryImageService();
        }
        return instance;
    }

    public UploadedImage uploadAuctionItemImage(CreateItemRequestMessage request) {
        if (request == null || isBlank(request.getImageBase64())) {
            return null;
        }
        if (cloudinary == null) {
            throw new ValidationException("Cloudinary is not configured.");
        }

        String contentType = normalizeContentType(request.getImageContentType());
        String base64Payload = request.getImageBase64().trim();
        validateBase64Image(base64Payload);

        String dataUri = "data:" + contentType + ";base64," + base64Payload;
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    dataUri,
                    ObjectUtils.asMap(
                            "folder", CLOUDINARY_FOLDER,
                            "resource_type", "image"
                    )
            );
            Object secureUrl = uploadResult.get("secure_url");
            Object publicId = uploadResult.get("public_id");
            if (secureUrl == null || secureUrl.toString().isBlank()
                    || publicId == null || publicId.toString().isBlank()) {
                throw new ValidationException("Cloudinary did not return an image URL.");
            }
            return new UploadedImage(secureUrl.toString(), publicId.toString());
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("Failed to upload image to Cloudinary.");
        }
    }

    public void deleteAuctionItemImage(UploadedImage uploadedImage) {
        if (uploadedImage == null || isBlank(uploadedImage.publicId()) || cloudinary == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(uploadedImage.publicId(), Map.of());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to remove uploaded auction image {}.", uploadedImage.publicId());
        }
    }

    private String normalizeContentType(String contentType) {
        if (isBlank(contentType)) {
            return DEFAULT_IMAGE_CONTENT_TYPE;
        }
        String normalized = contentType.trim().toLowerCase();
        if (!normalized.startsWith("image/")) {
            throw new ValidationException("Only image files can be uploaded.");
        }
        return normalized;
    }

    private void validateBase64Image(String base64Payload) {
        try {
            Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Image data is invalid.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record UploadedImage(String url, String publicId) {
    }
}
