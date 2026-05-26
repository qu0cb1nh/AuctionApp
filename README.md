# Auction App

[![CI](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml)

## Requirements:
- Java 25
- Maven
- Scene Builder

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


## Wallet Mechanism & Money Flow
The application uses an internal wallet system to manage funds securely during auctions.

### Dual-Balance Model
Users have two separate balance types to ensure financial commitment:
- Available Balance: Liquid funds for bidding or withdrawal.
- Pending Balance: Funds "locked" in active bids. These are restricted until the auction concludes.

### Deposit & Withdrawal
- Deposit: Funds are added directly to the Available Balance.
- Withdrawal: Only Available Balance can be withdrawn. Funds locked in bids cannot be moved until released.

### Money Flow Lifecycle
1. **Placing a Bid (Locking Funds):** When a bid is placed, the system verifies the user's Available Balance. If valid, the full bid amount is moved to the Pending Balance. The funds remain in the user's account but are no longer spendable.
2. **Updating a Bid (Incremental Commitment):** Increasing a bid only moves the incremental difference from Available to Pending.The previous bid amount remains unchanged
3. **Auction Settlement:** When an auction ends, the system automatically resolves the committed funds:
    - Winning Bidder: Committed funds are deducted from their Pending Balance and transferred to the seller's account.
    - Other Participants: Their locked funds are moved back from Pending to Available Balance, restoring their liquidity.
    - Seller: Receives the winning amount directly into their Available Balance upon successful settlement.

