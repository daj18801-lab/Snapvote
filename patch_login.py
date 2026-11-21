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

gs_path = "app/google-services.json"
if os.path.exists(gs_path):
    print("Real app/google-services.json found! Google Sign-In is ready.")
else:
    print("Creating temporary placeholder app/google-services.json so debug.keystore can build...")
    with open(gs_path, "w", encoding="utf-8") as f:
        f.write(f"""{{
  "project_info": {{
    "project_number": "123456789000",
    "project_id": "snapvote-placeholder",
    "storage_bucket": "snapvote-placeholder.appspot.com"
  }},
  "client": [
    {{
      "client_info": {{
        "mobilesdk_app_id": "1:123456789000:android:abcdef1234567890",
        "android_client_info": {{
          "package_name": "{pkg_name}"
        }}
      }},
      "oauth_client": [
        {{
          "client_id": "123456789000-placeholder.apps.googleusercontent.com",
          "client_type": 3
        }}
      ],
      "api_key": [
        {{
          "current_key": "AIzaSyPlaceholderKeyForBuildOnly12345"
        }}
      ],
      "services": {{
        "appinvite_service": {{
          "other_platform_oauth_client": [
            {{
              "client_id": "123456789000-placeholder.apps.googleusercontent.com",
              "client_type": 3
            }}
          ]
        }}
      }}
    }}
  ],
  "configuration_version": "1"
}}
""")
