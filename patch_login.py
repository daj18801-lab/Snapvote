import os
import re

gradle_path = "app/build.gradle.kts"
pkg_name = "com.example.snapvote"
if os.path.exists(gradle_path):
    with open(gradle_path, "r", encoding="utf-8") as f:
        match = re.search(r'applicationId\s*=\s*"([^"]+)"', f.read())
        if match:
            pkg_name = match.group(1)

print(f"Package Name (applicationId): {pkg_name}")

if os.path.exists("app/google-services.json"):
    print("Real app/google-services.json found! Google Sign-In is ready.")
else:
    print("WARNING: Upload your real google-services.json into the app/ folder for Google Sign-In to work!")
