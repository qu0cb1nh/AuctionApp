# Auction App

[![CI](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml)

## Project Description and System Scope

This auction application addresses the need for an online platform that enables users to buy and sell items through an auction mechanism.

The system provides an interactive environment where users can:
*   **Securely register and log in**.
*   **List items** for auction.
*   **Place bids** on desired items.
*   **Manage their e-wallet** for depositing and withdrawing funds.
*   **Monitor** auctions of interest and receive **notifications** about important updates.

**System Scope:**

The system is designed as a client-server application, encompassing:

*   **User Management and Authentication:** Ensuring secure access and account management.
*   **Auction Management:** Including creation, modification, cancellation, bidding, and closing of auctions.
*   **Financial Management:** An internal wallet system with deposit/withdrawal functionalities and balance management.
*   **Notification and Watchlist System:** Keeping users informed about auction statuses and items on their watchlist.
*   **Basic Administrative Functions:** Monitoring and managing users.




## Technologies & Requirements

This project leverages a modern stack to deliver a robust auction application:

*   **Java 25**: Core development language for client and server.
*   **JavaFX**: For the client's graphical user interface (GUI).
*   **Maven**: Build automation and dependency management.
*   **Scene Builder**: Visual UI design for JavaFX.
*   **MySQL**: Relational database for data persistence.
*   **Cloudinary**: Cloud service for image storage and management




## [Workflow guide](WORKFLOW.md)

## Setup guide
1. Clone the project:

- ssh (recommended, requires a configured SSH key on GitHub):
`git clone git@github.com:qu0cb1nh/AuctionApp.git`

- Tutorial for setting up SSH(use Git Bash or any Unix terminal): https://www.theodinproject.com/lessons/foundations-setting-up-git

- Or https (not recommended for workflow, but works without SSH setup):
`git clone https://github.com/qu0cb1nh/AuctionApp.git`

2. Running in Intellij

- Open the project in IntelliJ

- Open Project Structure (top left), set Project SDK to Java 25

- Start the Application

![img](https://i.ibb.co/wNW19zCj/idea64-0e2-C5-B8h-Ja.png)

Press run Server to start the server, then run Client to open the JavaFX application

### Local environment (.env)

Create a `.env` file in the repository root (do not commit it) and copy values from `.env.example`.

Example:
```
DB_URL=jdbc:mysql://host:port/db?sslMode=REQUIRED
DB_USER=your_username
DB_PASSWORD=your_password
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
```

Notes:
- `.env` is ignored by git and must stay local.
- Use real OS environment variables in production/CI instead of `.env`.




## Project Structure

The project is organized into three main modules, designed to separate concerns and facilitate development:

*   **`auction-client`**: This module contains the client-side application, built with JavaFX. It provides the graphical user interface (GUI) for users to interact with the auction system, including browsing auctions, placing bids, managing their wallet, viewing notifications and more.
*   **`auction-common`**: This module houses shared components, data transfer objects (DTOs), and communication message structures used by both the client and server. It defines the common language and data contracts, ensuring seamless interaction between the different parts of the system.
*   **`auction-server`**: This module comprises the server-side application. It manages the core business logic, handles client requests, interacts with the database (for user data, auctions, bids, and wallet balances), and processes real-time auction updates and notifications.




### Command Line Execution
To run the application directly from the command line, ensure you have correctly set

First, navigate to the project's root directory in your terminal.
1.  **Build the project (optional, but good practice):**
    ```bash
    mvn clean install
    ```

2.  **Run the Server (Must be started first!):**
    ```bash
    mvn exec:java -pl auction-server -Dexec.mainClass="net.auctionapp.server.ServerApp"
    ```
    This command starts the auction server. Keep this terminal window open.

3.  **Run the Client (Start after the server is running):**
    ```bash
    mvn exec:java -pl auction-client -Dexec.mainClass="net.auctionapp.client.ClientLauncher"
    ```
    This command launches the client application.
**Important Note on Environment Variables:**
   When running via the command line, ensure your environment variables (e.g., `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CLOUDINARY_URL`) are correctly loaded into shell session for each separate operating system.




## Features

The Auction App provides a robust platform with key functionalities:

### User Management & Authentication
*   **Secure User Registration & Login:** Users can create new accounts and securely log in to access the platform.
*   **Session Management:** The system handles user sessions, with server-side mechanisms for forced logouts when necessary.

### Comprehensive Auction System
*   **Browse & Search Auctions:** Easily view a list of active auctions with detailed information.
*   **Detailed Auction View:** Access comprehensive details for each auction, including item descriptions, current bids, and time remaining.
*   **Create & Manage Listings:** Users can list their own items for auction, providing descriptions, starting prices, and other relevant details. They also have the ability to update, cancel, or close their active listings.
*   **Dynamic Bidding:** Place bids on desired items, with real-time updates on current bid prices and auction status.
*   **Real-time Updates:** Receive immediate notifications on auction price changes, status updates, and when an auction concludes.
*   **Personal Activity Tracking:** Monitor all personal auction-related activities, including placed bids and won auctions.
*   **My Listings Overview:** Keep track of all items currently listed for sale.

### Financial Management (Internal Wallet)
*   **Secure Wallet System:** An integrated wallet system for managing funds within the application.
*   **Fund Deposit & Withdrawal:** Users can deposit funds into their available balance and withdraw them securely.
*   **Dual-Balance Model:** Funds are managed with an 'Available Balance' for liquid funds and a 'Pending Balance' for funds committed to active bids.

### Watchlist & Notifications
*   **Personalized Watchlist:** Users can add items to a watchlist to monitor auctions of interest.
*   **Watchlist Updates:** Receive real-time notifications about changes to items on the watchlist.
*   **In-App Notifications:** Access and manage a centralized list of all system and auction-related notifications.
*   **Notification Management:** Clear notifications once reviewed.

### Administrative Tools
*   **User Oversight:** Administrators have tools to view a list of all registered users.
*   **User Ban/Unban:** Administrative control to ban or unban users, ensuring platform integrity.


