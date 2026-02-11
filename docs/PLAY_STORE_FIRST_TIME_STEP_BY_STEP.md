# Play Store First-Time Onboarding — Step-by-Step

**App:** Cosmic Ocean  
**Version:** 2.7.1  
**Date:** 2026-02-05  
**Goal:** Publish the app to Google Play for the first time.

---

## 0) Start Here (URLs + Cost)
- **Signup URL:**  
  ```
  https://play.google.com/console/signup
  ```
- **Console URL:**  
  ```
  https://play.google.com/console
  ```
- **One-time cost:**  
  **$25 USD** (or local equivalent) for the Google Play Developer account.

---

## 1) Create Your Developer Account
1. Open the signup URL.
2. Sign in with the Google account you want to own the developer account.
3. Accept the Google Play Developer Distribution Agreement.
4. Pay the **$25 USD** one-time fee.
5. Complete your developer profile:
   - Developer name (public)
   - Contact email (public)
   - Address and phone (required)

---

## 2) Create the App in Play Console
1. Go to the Console URL.
2. Click **Create app**.
3. Fill out:
   - **App name:** Cosmic Ocean  
   - **Default language:** English (US) or your preference  
   - **App or game:** App  
   - **Free or paid:** Free (recommended)  
4. Confirm policy declarations and continue.

---

## 3) App Integrity (Play App Signing)
1. Navigate to **Setup → App integrity**.
2. Enable **Play App Signing** (required for new apps).
3. If asked for upload certificate:
   - Run this command:
     ```bash
     keytool -export -rfc -alias key0 -keystore android/app/keystore/release.jks -file upload_certificate.pem
     ```
   - Upload `upload_certificate.pem` in Play Console.

---

## 4) App Content (Policy Forms)
Go to **Policy** or **App content** and complete:

### Privacy Policy
- URL:  
  ```
  https://cosmic-ocean-production.up.railway.app/
  ```

### Data Safety
- **Data collected:** No  
- **Data shared:** No  
- **Data processed:** On-device only  

Use the draft:
```
docs/DATA_SAFETY_DRAFT.md
```

### Ads
- **Contains ads:** No

### App Access
- **All functionality available without special access**  
- No login required

### Target Audience
- Recommended: **13+**

---

## 5) Store Listing
Go to **Store presence → Main store listing**.

### Required fields
- **App name:** Cosmic Ocean  
- **Short description:** use `docs/STORE_LISTING_COPY.md`  
- **Full description:** use `docs/STORE_LISTING_COPY.md`  
- **Support email:** `cosmicocean625@gmail.com`  
- **Privacy policy URL:** `https://cosmic-ocean-production.up.railway.app/`

### Required graphics
- App icon (512x512)
- Feature graphic (1024x500)
- At least 2 phone screenshots (recommended 5+)

---

## 6) Build / Locate the AAB (Already Built)
Use the AAB at:
```
android/app/build/outputs/bundle/release/app-release.aab
```

---

## 7) Upload to Internal Testing (Recommended First)
1. Go to **Testing → Internal testing**.
2. Create a tester list (emails or Google Group).
3. Create a new release.
4. Upload AAB:
   ```
   android/app/build/outputs/bundle/release/app-release.aab
   ```
5. Add release notes (from `CHANGELOG.md` → 2.7.1).
6. Save → Review → Roll out to internal testing.

---

## 8) Promote to Production
1. Go to **Release → Production**.
2. Create a new release (or promote from internal testing).
3. Upload the same AAB or use “Promote”.
4. Add release notes.
5. Submit for review.

---

## 9) After Submission
- Wait for Google review (hours to days).
- Monitor for policy warnings.
- If rejected, follow Console guidance and resubmit.

---

## App-Specific Notes
- **Support email:** cosmicocean625@gmail.com  
- **Privacy policy URL:** https://cosmic-ocean-production.up.railway.app/  
- **Data safety:** Local-only, no data collected or shared  
- **App bundle:** android/app/build/outputs/bundle/release/app-release.aab

