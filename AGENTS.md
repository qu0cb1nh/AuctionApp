Bài Tập Lớn: Phát triển hệ thống đấu giá trực tuyến

2. Mô tả hệ thống
Hệ thống cho phép nhiều người dùng tham gia cạnh tranh giá để mua sản phẩm trong một khoảng thời gian xác định.

3. Các yêu cầu cụ thể

3.1 Chức năng bắt buộc

    Quản lý người dùng: 
      - Đăng ký/đăng nhập tài khoản (username, password, không cần email/phone).
      - Các vai trò: Bidder (người đấu giá), Seller (người bán), Admin (quản trị viên)
      - Giải thích về vai trò: Một người dùng hoàn toàn có thể vừa bán (seller) vừa đấu giá (bidder). Tuy nhiên, trong thiết kế hệ thống, việc phân biệt Seller và Bidder là để thể hiện vai trò (role) trong từng ngữ cảnh hoạt động. Khi user đăng sản phẩm thì đóng vai seller, khi đặt giá thì đóng vai bidder. Cách phân biệt này giúp hệ thống dễ xây dựng và kiểm soát các quy tắc nghiệp vụ, ví dụ như không cho phép seller đấu giá chính sản phẩm của mình (tránh trường hợp seller tăng giá ảo cho sp).

    Quản lý sản phẩm: Thêm/sửa/xóa sản phẩm với thông tin tên, mô tả, giá khởi điểm, giá hiện tại cao nhất, thời gian bắt đầu & kết thúc.

    Tham gia đấu giá cho phép người dùng đặt giá và theo dõi diễn biến của phiên đấu giá theo thời gian thực, đồng thời đảm bảo mọi giá đấu đều tuân thủ các quy tắc hợp lệ:
        - Giá đấu phải cao hơn giá hiện tại ít nhất một khoảng increment (ví dụ: 10.000 VND).
        - Kiểm tra tính hợp lệ của giá đấu
        - Cập nhật người dẫn đầu phiên đấu giá

    Kết thúc phiên: Tự động đóng phiên khi hết giờ, xác định người thắng và chuyển trạng thái (OPEN → RUNNING → FINISHED → PAID / CANCELED).

    Xử lý lỗi & ngoại lệ: Xử lý đặt giá thấp hơn giá hiện tại, đấu giá khi phiên đã đóng, lỗi kết nối/dữ liệu.

    Giao diện (GUI): Sử dụng JavaFX, các màn hình chính: Danh sách phiên đấu giá, chi tiết sản phẩm, màn hình đấu giá trực tiếp (realtime bidding), quản lý sản phẩm (dành cho seller)

3.2 Chức năng nâng cao

    Auto-Bidding: Tự động trả giá thay người dùng dựa trên maxBid và increment, Hệ thống tự động trả giá thay người dùng khi có bid mới từ đối thủ, logic: So sánh nhiều auto-bid cùng lúc, Không vượt quá maxBid, Ưu tiên theo thời điểm đăng ký auto-bid, Xử lý xung đột bid đồng thời

    Xử lý đấu giá đồng thời (Concurrent Bidding): Đảm bảo không bị Lost update, giá bị rollback hoặc hai người cùng thắng.

    Gia hạn phiên (Anti-sniping): Tự động gia hạn thêm Y giây nếu có bid mới trong X giây cuối.

    Realtime Update (Observer nâng cao): Cập nhật dữ liệu cho toàn bộ client ngay lập tức mà không dùng polling liên tục, gợi ý sử dụng: Observer Pattern, Socket / Event-based communication, Thread-safe notify

    Bid History Visualization: Hiển thị biểu đồ đường (Line Chart) giá đấu theo thời gian thực, mỗi bid hợp lệ là biểu đồ tự cập nhât không cần refresh

4. Thiết kế & Kiến trúc

4.1 Thiết kế hướng đối tượng (OOP)

    Thiết kế sơ đồ lớp (class diagram):
   - Entity (abstract) → User (abstract) → Bidder, Seller, Admin
   - Entity → Item (abstract) → Electronics, Art, Vehicle
   - Auction, BidTransaction
   - Art bao gồm: author, yearCreated; Electronics bao gồm: brand, model, warrantyMonths; Vehicle bao gồm: brand, model, yearCreated

    Nguyên tắc: Đảm bảo đủ Encapsulation, Inheritance, Polymorphism và Abstraction.

4.2 Kiến trúc hệ thống

    Mô hình: Client-Server.

    Giao tiếp: Socket (JSON).

    Phân tầng: MVC (Model-View-Controller) cho cả phía Client và Server.

4.3 Công nghệ & Công cụ

    Build tool: Maven hoặc Gradle.

    Unit Test: JUnit cho logic quan trọng.

    CI/CD: GitHub Actions (khuyến khích).

    Design Patterns: Singleton (AuctionManager), Factory Method (tạo Item), Observer, Strategy/Command.

5. Bảng điểm đánh giá
   Nội dung đánh giá	Điểm	Mức độ
   Thiết kế lớp và cây kế thừa	0.5	Bắt buộc
   Áp dụng đúng các nguyên tắc OOP	1.0	Bắt buộc
   Áp dụng design pattern phù hợp	1.0	Bắt buộc
   Quản lý người dùng, sản phẩm	1.0	Bắt buộc
   Chức năng đấu giá	1.0	Bắt buộc
   Xử lý lỗi & ngoại lệ	1.0	Bắt buộc
   Xử lý đấu giá đồng thời an toàn	1.0	Bắt buộc
   Realtime update (Observer/Socket)	0.5	Bắt buộc
   Thiết kế kiến trúc Client-Server	0.5	Bắt buộc
   Áp dụng MVC (JavaFX/Server DAO)	0.5	Bắt buộc
   Sử dụng Maven/Gradle, mã nguồn sạch	0.5	Bắt buộc
   Unit Test (JUnit)	0.5	Bắt buộc
   Thiết lập CI/CD cơ bản	0.5	Bắt buộc
   Các chức năng nâng cao (Auto-bid, Anti-sniping, Chart...)	Tối đa 1.5	Tùy chọn
   Tổng điểm	10 + 1

6. Các yêu cầu khác
   Phiên bản Java: Java 25
   Tất cả các chữ (như dòng text trong string, comments, docs,...) đều phải viết tiếng anh
   Các commit phải theo conventional commit: https://www.conventionalcommits.org/en/v1.0.0/
