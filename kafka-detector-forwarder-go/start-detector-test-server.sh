#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode()
        print(f"POST {self.path}")
        print(body)
        self.send_response(200)
        self.end_headers()

print("Detector test server listening on http://127.0.0.1:8091/detect")
HTTPServer(("127.0.0.1", 8091), Handler).serve_forever()
PY
