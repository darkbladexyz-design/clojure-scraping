# TF2 Fabricator Helper

A Clojure web service that scrapes Team Fortress 2 (TF2) kit fabricator data from the Steam Community Market. The service provides a web interface and JSON API to look up fabricator kits by name and retrieve their required inputs and outputs.

## What It Does

This application:
- Searches the Steam Community Market for TF2 fabricator kits by name
- Scrapes and parses kit information including required inputs and expected outputs
- Provides a simple web UI to search for kits
- Exposes a JSON API for programmatic access
- Uses multiple fallback strategies to extract data (static HTML parsing, Steam render API, and headless browser)

## Architecture

### Core Components

**`src/app/server.clj`** - Main web server
- Runs a Ring/Jetty web server on port 3000
- Serves an HTML interface for kit lookups
- Provides `/api/kit?name=<kit-name>` JSON endpoint
- Handles request routing and error handling

**`src/app/kits.clj`** - Steam market integration
- Searches Steam Community Market to resolve kit names to URLs
- Uses Steam's search API to find exact or fuzzy matches
- Generates proper market listing URLs with correct encoding

**`src/demo/hickory_page.clj`** - Multi-strategy scraper
- Attempts multiple parsing strategies in order of reliability:
  1. Steam render endpoint JSON parsing
  2. g_rgAssets JavaScript variable extraction from HTML
  3. Direct HTML descriptor parsing
  4. Headless browser hover capture (fallback)
- Returns structured kit data: `{:inputs [...] :outputs [...]}`

**`src/demo/hickory_fab.clj`** - Steam render JSON parser
- Fetches Steam's render endpoint JSON
- Extracts asset descriptions and parses fabricator requirements
- Helper functions for hickory_page.clj

**`src/demo/hover.clj`** - Headless browser scraper (fallback)
- Uses Etaoin + Chrome/ChromeDriver for dynamic content
- Simulates mouse hover to capture kit descriptions
- Only used when static parsing fails

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 11 or higher**
   - Download from [Adoptium](https://adoptium.net/) or your preferred JDK provider

2. **Leiningen** (Clojure build tool)
   - Install from [leiningen.org](https://leiningen.org/)
   - Windows: Download `lein.bat` and add to your PATH, or install via Scoop: `scoop install leiningen`
   - *Alternative*: Clojure CLI tools from [clojure.org](https://clojure.org/guides/install_clojure)

3. **(Optional) Chrome + ChromeDriver** - For headless browser fallback
   - Only needed if static parsing fails
   - Chrome: [google.com/chrome](https://www.google.com/chrome/)
   - ChromeDriver: [chromedriver.chromium.org](https://chromedriver.chromium.org/)
   - Must match your Chrome version
   - Set environment variables if not in standard locations:
     - `CHROME_BIN` - Path to Chrome executable
     - `CHROMEDRIVER_PATH` - Path to chromedriver executable

## Installation

1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd clojure-scraping
   ```

2. Dependencies are automatically downloaded when you first run the project (specified in `deps.edn`)

## Running the Server

Start the web server using Leiningen:

```bash
lein run
```

Or if you have Clojure CLI tools installed:
```bash
clj -M:run
```

The server will start on **http://localhost:3000**

You should see output like:
```
=== Starting server with debug logging enabled ===
Server on http://localhost: 3000
Server ready. Try accessing:
1. Main page: http://localhost:3000
2. Test endpoint: http://localhost:3000/api/test
```

## Usage

### Web Interface

1. Open http://localhost:3000 in your browser
2. Enter a kit name in the input field (e.g., "Specialized Killstreak Rocket Launcher Kit Fabricator")
3. Click "Fetch" to retrieve kit information
4. Results display as JSON showing inputs and outputs

### API Endpoint

**GET** `/api/kit?name=<kit-name>`

Example:
```bash
curl "http://localhost:3000/api/kit?name=Specialized%20Killstreak%20Rocket%20Launcher%20Kit%20Fabricator"
```

Response:
```json
{
  "name": "Specialized Killstreak Rocket Launcher Kit Fabricator",
  "url": "https://steamcommunity.com/market/listings/440/...",
  "inputs": ["Battle-Worn Robot Taunt Processor x 11", ...],
  "outputs": ["Specialized Killstreak Rocket Launcher Kit", ...]
}
```

### Test Endpoint

**GET** `/api/test` - Simple health check endpoint

```bash
curl http://localhost:3000/api/test
```

## Dependencies

Listed in `deps.edn`:

- **org.clojure/clojure** (1.11.3) - Core Clojure language
- **http-kit/http-kit** (2.8.0) - HTTP client for fetching Steam pages
- **hickory/hickory** (0.7.1) - HTML parsing library
- **org.clojure/data.json** (2.5.0) - JSON encoding/decoding
- **ring/ring-core** (1.11.0) - Web application library
- **ring/ring-jetty-adapter** (1.11.0) - Jetty web server adapter
- **etaoin/etaoin** (1.0.40) - WebDriver automation (headless browser fallback)

## Project Structure

```
clojure-scraping/
├── deps.edn              # Project dependencies and configuration
├── CHANGES.md            # Development changelog
├── README.md             # This file
├── geckodriver           # Firefox WebDriver (legacy, not used)
└── src/
    ├── app/
    │   ├── server.clj    # Web server and API routes
    │   └── kits.clj      # Steam market search and URL resolution
    └── demo/
        ├── hickory_page.clj  # Multi-strategy kit data scraper
        ├── hickory_fab.clj   # Steam render JSON parser
        └── hover.clj         # Headless browser hover capture
```

## Development Notes

### How It Works

1. **User requests a kit by name** via web UI or API
2. **`kits/name->url`** searches Steam's market API to resolve the name to a listing URL
3. **`hickory-page/fetch-kit`** attempts multiple parsing strategies:
   - First tries Steam's `/render` endpoint which returns JSON
   - Falls back to parsing JavaScript variables embedded in HTML
   - Falls back to parsing HTML descriptor divs directly
   - Final fallback: launches headless Chrome to hover and capture descriptions
4. **Response** is cleaned, parsed, and split into inputs/outputs
5. **JSON response** sent back to client

### Common Issues

**Steam Rate Limiting**: If you make too many requests, Steam may temporarily block your IP. Wait a few minutes and try again.

**ChromeDriver Version Mismatch**: If using headless fallback, ensure ChromeDriver version matches your Chrome version exactly.

**Network Errors**: The app requires internet connectivity to access Steam Community Market.

**Parse Failures**: Some kits may not have active listings, in which case descriptors won't be available. The app will return a note explaining this.

## Stopping the Server

Press `Ctrl+C` in the terminal where the server is running to shut it down gracefully.

## License

This project is for educational and personal use. Steam Community Market content is property of Valve Corporation.

## Contributing

This is a personal project, but suggestions and improvements are welcome via issues or pull requests.
