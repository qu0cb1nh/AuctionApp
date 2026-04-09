package net.auctionapp.client.controllers;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import net.auctionapp.client.SceneNavigator;
public class AuctionItemController {

    // 1. Ánh xạ các UI components dựa trên fx:id trong FXML
    @FXML private ImageView productImageView;
    @FXML private Label productNameLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label currentBidLabel;
    @FXML private Label timeRemainingLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button placeBidButton;
    @FXML private Label messageLabel;
    @FXML private LineChart<String, Number> priceHistoryChart;

    // Biến lưu trữ giá hiện tại để kiểm tra logic
    private double currentHighestBid = 1199.0;

    // 2. Hàm initialize() tự động chạy khi FXML được load xong
    @FXML
    public void initialize() {
        // Reset thông báo lỗi
        messageLabel.setText("");
    }

    // 3. Hàm xử lý sự kiện khi người dùng bấm nút "Bid now!"
    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            // Lấy số tiền người dùng nhập
            String input = bidAmountField.getText();
            double newBid = Double.parseDouble(input);

            // Kiểm tra tính hợp lệ
            if (newBid <= currentHighestBid) {
                messageLabel.setText("Lỗi: Giá đấu phải cao hơn $" + currentHighestBid);
                messageLabel.setStyle("-fx-text-fill: red;");
            } else {
                // Đặt giá hợp lệ -> Gửi lên Server (hoặc Model) tại đây
                currentHighestBid = newBid;
                currentBidLabel.setText("$" + currentHighestBid);
                messageLabel.setText("Đặt giá thành công!");
                messageLabel.setStyle("-fx-text-fill: green;");
                bidAmountField.clear();

                // Cập nhật biểu đồ (chức năng nâng cao) ở đây...
            }
        } catch (NumberFormatException e) {
            messageLabel.setText("Lỗi: Vui lòng nhập số hợp lệ.");
            messageLabel.setStyle("-fx-text-fill: red;");
        }
    }
    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene("views/AuctionList.fxml");
    }
}