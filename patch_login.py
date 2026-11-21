import os
import re
import glob

# 1. Detect applicationId from app/build.gradle.kts
gradle_path = "app/build.gradle.kts"
pkg_name = "com.example.snapvote"
if os.path.exists(gradle_path):
    with open(gradle_path, "r", encoding="utf-8") as f:
        match = re.search(r'applicationId\s*=\s*"([^"]+)"', f.read())
        if match:
            pkg_name = match.group(1)

print(f"Detected Package Name (applicationId): {pkg_name}")

# 2. Create placeholder google-services.json only if a real one isn't uploaded
gs_path = "app/google-services.json"
if not os.path.exists(gs_path):
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
    print("Created placeholder app/google-services.json")

# 3. Bypass Google Sign-In in GoogleAuthHelper.kt so login opens the app immediately
auth_files = glob.glob("**/GoogleAuthHelper.kt", recursive=True)
for auth_file in auth_files:
    with open(auth_file, "r", encoding="utf-8") as f:
        code = f.read()

    # Replace credentialManager.getCredential(...) calls so sign-in succeeds immediately with a mock ID token
    patched = re.sub(
        r'credentialManager\.getCredential\s*\([^;{}]*?\)\s*',
        'throw Exception("GUEST_BYPASS") ',
        code,
        flags=re.DOTALL
    )

    # Fallback: if GoogleAuthHelper uses a suspend sign-in function, return a dummy guest token/user
    if "GUEST_BYPASS" in patched:
        patched = patched.replace(
            'catch (e: Exception) {',
            'catch (e: Exception) {\n            if (e.message == "GUEST_BYPASS") return Result.success("guest_bypass_token")'
        )
        with open(auth_file, "w", encoding="utf-8") as f:
            f.write(patched)
        print(f"Patched guest login bypass in: {auth_file}")
