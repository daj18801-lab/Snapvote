import os
import re
import json

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

# 3. Extract Web Client ID (client_type == 3) from app/google-services.json
web_client_id = None
gs_path = "app/google-services.json"
if os.path.exists(gs_path):
    with open(gs_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    print("PROJECT_ID:", data.get("project_info", {}).get("project_id"))
    for c in data.get("client", []):
        pkg = c.get("client_info", {}).get("android_client_info", {}).get("package_name")
        print("CLIENT_PACKAGE:", pkg)
        for oauth in c.get("oauth_client", []):
            if oauth.get("client_type") == 3:
                web_client_id = oauth.get("client_id")
        if not web_client_id:
            for oauth in c.get("services", {}).get("appinvite_service", {}).get("other_platform_oauth_client", []):
                if oauth.get("client_type") == 3:
                    web_client_id = oauth.get("client_id")
    print("EXTRACTED_WEB_CLIENT_ID:", web_client_id)

# 4. Write default_web_client_id into app/src/main/res/values/strings.xml so R.string.default_web_client_id ALWAYS compiles
res_dir = "app/src/main/res/values"
os.makedirs(res_dir, exist_ok=True)
strings_path = os.path.join(res_dir, "strings.xml")

if os.path.exists(strings_path):
    with open(strings_path, "r", encoding="utf-8") as f:
        xml = f.read()
else:
    xml = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'

if "default_web_client_id" not in xml:
    client_val = web_client_id if web_client_id else "MISSING_WEB_CLIENT_ID"
    entry = f'    <string name="default_web_client_id" translatable="false">{client_val}</string>\n'
    xml = xml.replace("</resources>", entry + "</resources>")
    with open(strings_path, "w", encoding="utf-8") as f:
        f.write(xml)
    print(f"Added default_web_client_id={client_val} to strings.xml")
