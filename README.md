# Auction App

[![CI](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml)

## Project Description and System Scope

Auction App is a client-server auction application where users can create listings, browse items, and place bids in active auction sessions. It also supports wallets, watchlists, notifications, and basic administrator controls.

System scope includes a JavaFX desktop client, a Java socket server, JSON messaging, and MySQL persistence. The server handles user sessions, role-based access, auction lifecycle management, bid validation, wallet balance updates, real-time notifications, and concurrency-safe bidding.

## Features

### User Management

* Register and log in.
* User and admin roles.
* Session tracking and forced logout.

### Auction System

* Browse, search, and filter auctions.
* View item details, price, bidder, time, and bid chart.
* Create, edit, cancel, and close listings.
* Validate bids and minimum increments.
* Protect concurrent bidding with server-side locking.
* Auto-close expired auctions.
* Extend near-ended auctions with anti-sniping logic.
* Push real-time updates to clients.
* Track bids, wins, and seller listings.

### Wallet

* View balances.
* Deposit and withdraw funds.
* Track available and pending funds.

### Watchlist and Notifications

* Add auctions to watchlist.
* Receive auction updates.
* View and clear notifications.

### Admin Tools

* Manage users: view user list, ban/unban user
* Manage auctions: edit auction details, close/cancel auction

## Tech Stack

* Java 25: Core development language for client and server
* JavaFX 25.0.2: For client's GUI
* Maven: Build automation and dependency management.
* Scene Builder: Visual UI design for JavaFX.
* MySQL: Relational database for data persistence.
* Cloudinary: Cloud service for image storage and management

Normal users only need Java 25 and the released user JAR.

## Project Structure
```text
|-- .github/workflows/    Github actions workflow files
|-- auction-client/       JavaFX app, FXML screens, controllers, client services
|-- auction-server/       Socket server, managers, DAOs, models and business logic.
|-- auction-common/       Shared DTOs, enums, messages, and JSON helpers.
|-- docs/                 System architecture, report and uml diagrams
|-- pom.xml               Parent Maven build
|-- README.md             Information about the app
```


## Architecture and Diagrams

* Client-server architecture.
* MVC-style layers.
* Socket JSON protocol.
* [System architecture](docs/system-architecture.png)
* [Models diagram](docs/diagrams/models-diagram/models-diagram.png)

## Installation and Running

### For Users

* Install Java 25.
* Download `AuctionApp-<version>.jar` from [GitHub releases](https://github.com/qu0cb1nh/AuctionApp/releases).
* Run normally: Double-click the `AuctionApp-<version>.jar` file to open the client
* Run from command:

  ```bash
  java -jar AuctionApp-<version>.jar
  ```

* Public user JAR connects to the public server.
* No local database setup needed.
* No local Cloudinary setup needed.

### For Developers and Local Testing

* JDK 25.
* MySQL database access.
* Cloudinary account for image upload.
* Local JARs from [GitHub releases](https://github.com/qu0cb1nh/AuctionApp/releases):

  ```text
  AuctionApp-<version>-Local-Server.jar
  AuctionApp-<version>-Local-Client.jar
  ```

* Create `.env` beside the local server JAR:

  ```env
  DB_URL=jdbc:mysql://host:port/db?sslMode=REQUIRED
  DB_USER=your_username
  DB_PASSWORD=your_password
  CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
  ```

* OS environment variables override `.env`.
* MySQL database must already exist.
* Server creates required tables automatically.
* Local client uses `localhost:3667`.
* Change `auction-client/src/main/resources/client.properties` for another host or port.
* Run local server:

  ```bash
  java -jar AuctionApp-<version>-Local-Server.jar
  ```

* Run local client:

  ```bash
  java -jar AuctionApp-<version>-Local-Client.jar
  ```

## Workflow Guide for Contributors

* See [WORKFLOW.md](WORKFLOW.md).

## Report and Demo

* Report PDF: [report.pdf](./docs/report.pdf)
* Demo video: [auctionapp_demo](https://drive.google.com/file/d/1JYPCfgOzePoTlcG2pmrUXt2xjPJGtBvx/view?usp=drive_link)
