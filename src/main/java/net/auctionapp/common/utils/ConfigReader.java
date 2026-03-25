package net.auctionapp.common.utils;

import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final Properties properties = new Properties();

    // Khối static này sẽ tự động chạy 1 lần duy nhất khi class được gọi
    static {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Xin lỗi, không tìm thấy file application.properties");
            } else {
                // Tải dữ liệu từ file vào biến properties
                properties.load(input);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Viết các hàm getter để lấy giá trị ra
    public static int getServerPort() {
        String portStr = properties.getProperty("server.port", "5000");
        return Integer.parseInt(portStr);
    }

    public static String getServerHost() {
        return properties.getProperty("server.host", "localhost");
    }
}
