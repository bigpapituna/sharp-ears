# Pictures for the parent brief by email

The 27 pup pictures in `email/` are the ones the brief's email shows (the postcard pup in each of the eight sweatband
colours, the pup saying the line, the crown). They are byte for byte the app's own `app/src/main/assets/email/*.png`
(cut by `scripts/cut_email_pups.py`; a test fails if the two folders differ).

Gmail and some other mail apps do not show pictures carried inside an email (`data:` pictures): until the pictures are
hosted, those readers see the alt text instead ("The blue pup dancing"). Once they are hosted at a public https address
and the build knows it, every email links to them there, and the HTML is a third of the size.

## What the build does

`secrets.properties` (gitignored) at the repo root may hold `EMAIL_IMAGE_BASE`, read into `BuildConfig.EMAIL_IMAGE_BASE`:

- empty (the default): the pictures go inside the email, as today;
- an `https://` address: every picture is `<EMAIL_IMAGE_BASE>/<file name>`, for example
  `https://<you>.github.io/sharp-ears-email/email/pup-crown.png`. A slash at the end of the base is ignored. The in-app
  preview always shows the pictures from inside the app and loads nothing.

So the host must serve each file of `email/` under its exact name (lower case, `.png`), directly at that address, with
no login, no redirect to a sign-in page, and no renaming.

## Hosting them (Roy, once)

Recommended: a public GitHub repository with GitHub Pages.

1. github.com > New repository, for example `sharp-ears-email`, **Public**, nothing else in it.
2. Upload the folder `email/` from here (Add file > Upload files, drag the folder in, Commit). The repository then has
   `email/pup-crown.png` and the 26 others.
3. Settings > Pages > Build and deployment: Deploy from a branch, `main`, `/ (root)`, Save. After a minute the site is at
   `https://<you>.github.io/sharp-ears-email/`.
4. Check in a browser that `https://<you>.github.io/sharp-ears-email/email/pup-crown.png` shows the pup with the crown.
5. Set the base (it asks for nothing else):
   `.\scripts\set-email-key.ps1 -ImageBase https://<you>.github.io/sharp-ears-email/email -PicturesOnly`
   (or give `-ImageBase` along with the key the first time). Then `.\gradlew.bat installDebug`.
6. Send the test email (the child's Email page, "Send me a test email now") and open it in Gmail: the pups show. Gmail
   fetches pictures through its own proxy, so GitHub sees Google, not the reader.

Also fine: `https://raw.githubusercontent.com/<you>/sharp-ears-email/main/email` (no Pages needed; GitHub serves them as
PNGs). Avoid Brevo's image library unless the addresses it gives keep the file names: it usually renames uploads
(`.../images/<number>.png`), and the app builds each address from the base and the file's own name.

Nothing in the pictures is personal: they are the same 27 files for every family. The host only learns that an email
was opened (and, for Gmail, not even by whom). The privacy page names the host when a build has one.

## When the pictures change

Re-run `scripts/cut_email_pups.py`, copy `app/src/main/assets/email/*.png` over `hosting/email/` (the test
`EmailHtmlTest.hostedPicturesAreTheBaseThenTheFileName` checks they match), and upload them to the host again under the
same names.
