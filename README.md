# WebApp Hardware Bridge

## Introduction

WebApp Hardware Bridge made it possible for WebApps to perform silent print and access to serial ports.

Common use cases:
- Web-based POS - PDF and ESC/POS receipt silent print
- Web-based WMS - Serial weight scale real-time reading, delivery note/packing List silent print
- Any WebApps need to read/write to serial ports

## Features

- [x] Direct print from WebApps
- [x] Serial port read/write from WebApps
- [x] Support all modern browsers that implemented WebSocket (Chrome, Firefox, Edge... etc)
- [x] [HTTP API](HTTP_API.md) to configure directly from your WebApp
- [x] [JS SDK/Example included](demo)

### Direct Print
- 0-click silent printing in web browsers
- Download via URL / Base64 encoded file / Base64 encoded binary raw command
- Support multiple printers, mapped by key
- Support PDF/PNG/JPG Printing
- Support RAW/ESC-POS Printing
- Support adding annotation text to PDF/Image before printing
- Per printer settings

### Serial Access
- Bidirectional communication
- Support multiple ports, mapped by key
- Support multiple connection share same serial port
- Serial weigh scale (AWH-SA30 supported out-of-box in JS SDK)
- Per port settings (Baud rate, data bits, stop bit, parity bit)

## How to use?

### Client Side

1. Install and setup mapping via Web UI / API

2. Start "WebApp Hardware Bridge" and start using your WebApp

### WebApp Side

1. Check [JS SDK/Example](demo)

## How it works?

WebApp Hardware Bridge is a Java based application, which has more access to underlying hardware.

It exposes a WebSocket server on localhost to accept print jobs and serial connections from browsers.

### Print Jobs 

- PDF/Images job are downloaded/decoded and then sent to mapped printer.
- Raw job are sent to mapped printer directly.

### Serial Connections

- Serial port are opened by Java and "proxied" as WebSocket stream
- Serial port can be shared by multiple connections
- Bidirectional communications possible

### Mappings

Web UI / API are provided to set up mappings between keys and printers/serials.

Therefore, WebApps do not need to care about the actual printer names.

## Example Printer Configuration

Here is an example of the `printer` section in `config.json`:

```json
  "printer": {
    "enabled": true,
    "autoAddUnknownType": false,
    "fallbackToDefault": false,
    "mappings": [
      {
        "type": "printer",
        "name": "Zebra_printer",
        "autoRotate": false,
        "resetImageableArea": true,
        "forceDPI": 0,
        "customLprCommand": "lpr -P {printer} -o media={paperSize} -o fit-to-page {file}",
        "paperSize": "Custom.100x150mm"
      },
      {
        "type": "printer2",
        "name": "Xerox_printer",
        "autoRotate": false,
        "resetImageableArea": true,
        "forceDPI": 0,
        "paperSize": "iso_a4_210x297mm"
      }
    ]
  }
```

This configuration allows WebApps to connect to printers via **WebSockets** and defines **printer settings** such as mappings, DPI settings, and printing commands.

## More documents

- [Configurations](CONFIGURATION.md)
- [HTTP APIs](HTTP_API.md)
- [Advanced Configurations - Authentication](ADVANCED.md#authentication)
- [Advanced Configurations - HTTPS/WSS Support](ADVANCED.md#httpswss-support)
- [Build from source](BUILD.md)
- [Troubleshooting](TROUBLESHOOT.md)

## Upgrade

- Settings will be lost after upgrading from 0.x to 1.0, please reconfigure via "Web UI" or "Web API"

## Changelogs

- [Changelogs](CHANGELOG.md)
