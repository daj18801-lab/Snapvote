import os
import re

# 1. Remove foojay-resolver-convention from settings.gradle.kts so Gradle initializes cleanly
settings_path = "settings.gradle.kts"
if os.path.exists(settings_path):
    with open(settings_path, "r", encoding="utf-8") as f:
        s_code = f.read()
    s_code_new = re.sub(r'.*foojay-resolver-convention.*\n?', '', s_code)
    if s_code != s_code_new:
        with open(settings_path, "w", encoding="utf-8") as f:
            f.write(s_code_new)
        print("Removed foojay-resolver-convention from settings.gradle.kts")

# 2. Point gradle-wrapper.properties directly to Gradle 9.3.1 without running `gradle wrapper`
wrapper_dir = "gradle/wrapper"
os.makedirs(wrapper_dir, exist_ok=True)
wrapper_props = os.path.join(wrapper_dir, "gradle-wrapper.properties")
with open(wrapper_props, "w", encoding="utf-8") as f:
    f.write(
        "distributionBase=GRADLE_USER_HOME\n"
        "distributionPath=wrapper/dists\n"
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.3.1-bin.zip\n"
        "zipStoreBase=GRADLE_USER_HOME\n"
        "zipStorePath=wrapper/dists\n"
    )
print("Configured gradle-wrapper.properties for Gradle 9.3.1")

# 3. Detect applicationId from app/build.gradle.kts
gradle_path = "app/build.gradle.kts"
pkg_name = "com.example.snapvote"
if os.path.exists(gradle_path):
    with open(gradle_path, "r", encoding="utf-8") as f:
        match = re.search(r'applicationId\s*=\s*"([^"]+)"', f.read())
        if match:
            pkg_name = match.group(1)

print(f"Package Name (applicationId): {pkg_name}")

# 4. Create temporary placeholder google-services.json only if real one is not uploaded
gs_path = "app/google-services.json"
if os.path.exists(gs_path):
    print("Real app/google-services.json found! Google Sign-In is ready.")
else:
    print("Creating temporary placeholder app/google-services.json so debug.keystore can build...")
    with open(gs_path, "w", encoding="utf-8") as f:
        f.write(f"""{{
  "project_info": {{
    "project_number": "123456789000",
    "project_id": "snapvote-3a283",
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
