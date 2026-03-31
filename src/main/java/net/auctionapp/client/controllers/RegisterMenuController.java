package net.auctionapp.client.controllers;


import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterMenuController {

    // Khai báo các biến tương ứng với các ô nhập dữ liệu trên giao diện
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    // Hàm này sẽ chạy khi người dùng bấm nút "GET STARTED"
    @FXML
    public void handleRegister() {
        // Lấy chữ mà người dùng vừa gõ vào
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Kiểm tra xem người dùng có để trống ô nào không
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            System.out.println("Error: Please fill in all the information!");
            return; // Dừng lại, không làm tiếp
        }

        // Kiểm tra xem 2 ô mật khẩu có giống nhau không
        if (!password.equals(confirmPassword)) {
            System.out.println("Error: Verification password does not match!");
            return;
        }

        // Nếu qua hết các ải trên thì coi như hợp lệ
        System.out.println("Congratulations! Account registration is in progress: " + username);
        System.out.println("=> TODO: Chỗ này sau này sẽ gọi hàm lưu vào Database!");

        // (Tùy chọn) Chuyển người dùng về lại màn hình Đăng nhập sau khi đăng ký xong
    }
}
