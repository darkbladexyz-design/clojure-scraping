# TF2 Fabricator Kit Helper

A web application that helps Team Fortress 2 players understand what items are required to craft Killstreak Fabricator Kits by scraping data from the Steam Community Market.

## Overview

This project provides a simple web interface where users can select a TF2 Fabricator Kit and see all the input items required to complete it. The application fetches real-time data from Steam Community Market listings and parses the fabricator requirements.

## Current Features

- **Web Interface**: Simple HTML interface for selecting fabricator kits
- **Kit Analysis**: Displays all required input items with quantities
- **Steam Market Integration**: Scrapes live data from Steam Community Market
- **REST API**: JSON API endpoint for programmatic access

## Future Enhancements

The next iteration of this project will include:

- **Profitability Calculator**: Calculate the total cost of purchasing all required items versus the selling price of the completed kit
- **Price Tracking**: Real-time price data from Steam Market for all inputs and outputs
- **ROI Analysis**: Show potential profit/loss for each fabricator kit
- **Multiple Kit Support**: Expanded dropdown with all available TF2 fabricator kits
- **Price History**: Track price trends over time

## Technology Stack

- **Language**: Clojure 1.11.3
- **Web Server**: Ring + Jetty
- **HTML Templating**: Hiccup
- **HTTP Client**: http-kit
- **HTML Parsing**: Hickory
- **Build Tool**: Leiningen

## Prerequisites

- **Java 11+** (Java 21 recommended)
- **Leiningen** (Clojure build tool)

### Windows Installation

1. **Install Java 21** (if not already installed):
   ```powershell
   scoop bucket add java
   scoop install temurin21-jdk
   ```

2. **Install Leiningen**:
   ```powershell
   scoop install leiningen
   ```

3. **Verify installations**:
   ```powershell
   java -version    # Should show Java 21
   lein --version   # Should show Leiningen version
   ```

## Running the Application

1. **Navigate to the project directory**:
   ```powershell
   cd path\to\clojure-scraping
   ```

2. **Start the server**:
   ```powershell
   lein run
   ```

   The server will start on port 3000. You should see:
   ```
   Server on http://localhost: 3000
   ```

3. **Access the web interface**:
   - Open your browser to: `http://localhost:3000`

4. **Stop the server**:
   - Press `Ctrl+C` in the terminal

## Project Structure

```
clojure-scraping/
├── project.clj           # Leiningen project configuration
├── README.md            # This file
├── .gitignore          # Git ignore patterns
└── src/
    └── app/
        ├── server.clj   # Web server and HTTP handlers
        └── kits.clj     # Steam Market scraping logic
```

## API Endpoints

### GET /
Returns the HTML web interface.

### GET /api/kit?name=<kit-name>
Fetches fabricator kit data and returns JSON.

**Example Request**:
```
GET /api/kit?name=Specialized%20Killstreak%20Rocket%20Launcher%20Kit%20Fabricator
```

**Example Response**:
```json
{
  "inputs": [
    {"item": "Battle-Worn Robot Taunt Processor", "qty": 8},
    {"item": "Pristine Robot Currency Digester", "qty": 4}
  ],
  "outputs": [
    "Specialized Killstreak Rocket Launcher Kit"
  ]
}
```

## How It Works

1. **User selects a kit** from the dropdown menu
2. **Application fetches** the Steam Market listing page
3. **Hickory parses** the HTML to extract item descriptors
4. **Data extraction** identifies input requirements and output items
5. **Results displayed** in the web interface as JSON

## Development

### Running a REPL

```powershell
lein repl
```

### Building an uberjar

```powershell
lein uberjar
```

This creates a standalone JAR file that can be run with:
```powershell
java -jar target/clojure-scraping-0.1.0-SNAPSHOT-standalone.jar
```

## Known Limitations

- Currently supports a limited set of fabricator kits (hardcoded in dropdown)
- No price data integration yet
- Error handling could be improved for Steam Market changes
- No caching of results (fetches fresh data on every request)

## Contributing

This is a personal project, but suggestions and improvements are welcome!

## License

This project is provided as-is for educational purposes.

## Disclaimer

This tool is not affiliated with Valve Corporation or Steam. It simply scrapes publicly available data from Steam Community Market. Use responsibly and in accordance with Steam's Terms of Service.
