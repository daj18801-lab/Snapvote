import os
import re
import json
import glob

# 1. Remove foojay-resolver-convention from settings.gradle.kts
if os.path.exists("settings.gradle.kts"):
    with open("settings.gradle.kts", "r", encoding="utf-8") as f:
        s_code = f.read()
    s_new = re.sub(r'.*foojay-resolver-convention.*\n?', '', s_code)
    if s_code != s_new:
        with open("settings.gradle.kts", "w", encoding="utf-8") as f:
            f.write(s_new)

# 2. Force app/build.gradle.kts to sign debug builds with root debug.keystore
app_gradle = "app/build.gradle.kts"
if os.path.exists(app_gradle):
    with open(app_gradle, "r", encoding="utf-8") as f:
        g_code = f.read()
    if 'rootProject.file("debug.keystore")' not in g_code:
        signing_block = '''
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
'''
        g_code = re.sub(r'(android\s*\{)', r'\1' + signing_block, g_code, count=1)
        with open(app_gradle, "w", encoding="utf-8") as f:
            f.write(g_code)
        print("Forced app/build.gradle.kts to sign debug APK with root debug.keystore")

# 3. Search ALL clients in app/google-services.json for a client_type == 3 Web Client ID
web_client_id = None
gs_path = "app/google-services.json"
if os.path.exists(gs_path):
    with open(gs_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    print("FIREBASE_PROJECT_ID:", data.get("project_info", {}).get("project_id"))
    for c in data.get("client", []):
        pkg = c.get("client_info", {}).get("android_client_info", {}).get("package_name")
        print("FOUND_CLIENT_PACKAGE:", pkg)
        for oauth in c.get("oauth_client", []):
            if oauth.get("client_type") == 3:
                web_client_id = oauth.get("client_id")
                print("EXACT_WEB_CLIENT_ID:", web_client_id)
        if not web_client_id:
            for oauth in c.get("services", {}).get("appinvite_service", {}).get("other_platform_oauth_client", []):
                if oauth.get("client_type") == 3:
                    web_client_id = oauth.get("client_id")
                    print("EXACT_WEB_CLIENT_ID (from appinvite):", web_client_id)
else:
    print("WARNING: app/google-services.json not found!")

# 4. Inject the Web Client ID directly into GoogleAuthHelper.kt so R.string.default_web_client_id is never needed
if web_client_id:
    for auth_file in glob.glob("**/GoogleAuthHelper.kt", recursive=True):
        with open(auth_file, "r", encoding="utf-8") as f:
            code = f.read()
        code = re.sub(r'context\.getString\(R\.string\.default_web_client_id\)', f'"{web_client_id}"', code)
        code = re.sub(r'getString\(R\.string\.default_web_client_id\)', f'"{web_client_id}"', code)
        with open(auth_file, "w", encoding="utf-8") as f:
            f.write(code)
        print(f"Injected {web_client_id} into {auth_file}")
else:
    print("ERROR: No client_type 3 Web Client ID found anywhere in app/google-services.json!")
