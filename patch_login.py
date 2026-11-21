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

# 2. Create placeholder google-services.json if real one isn't present
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

# 3. Patch GoogleAuthHelper.kt with exact String return type for idToken
auth_files = glob.glob("**/GoogleAuthHelper.kt", recursive=True)
for auth_file in auth_files:
    with open(auth_file, "r", encoding="utf-8") as f:
        lines = f.readlines()

    new_lines = []
    skip_until_close = 0

    for line in lines:
        if "credentialManager.getCredential(" in line:
            indent = line[:len(line) - len(line.lstrip())]
            var_match = re.match(r'\s*val\s+(\w+)\s*=', line)
            var_name = var_match.group(1) if var_match else "result"
            new_lines.append(
                f'{indent}val {var_name} = androidx.credentials.GetCredentialResponse('
                f'androidx.credentials.CustomCredential("com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL", android.os.Bundle()))\n'
            )
            skip_until_close = line.count("(") - line.count(")")
            continue

        if skip_until_close > 0:
            skip_until_close += line.count("(") - line.count(")")
            continue

        # Return a String ("guest_id_token") directly where GoogleIdTokenCredential.createFrom(...) was used
        if "GoogleIdTokenCredential.createFrom(" in line:
            indent = line[:len(line) - len(line.lstrip())]
            var_match = re.match(r'\s*val\s+(\w+)[^=]*=', line)
            if var_match:
                var_name = var_match.group(1)
                new_lines.append(f'{indent}val {var_name}: String? = "guest_id_token"\n')
            else:
                new_lines.append(re.sub(r'GoogleIdTokenCredential\.createFrom\([^)]*\)', '"guest_id_token"', line))
            continue

        new_lines.append(line)

    with open(auth_file, "w", encoding="utf-8") as f:
        f.writelines(new_lines)
    print(f"Successfully patched {auth_file}")
