// Downloads the Poppins font (SIL OFL) and Material icons (Apache 2.0) into the project.
// Run from the project root: node tools/fetch_assets.js
const fs = require('fs');
const FONT_BASE = 'https://raw.githubusercontent.com/google/fonts/main/ofl/poppins/';
const ICON_BASE = 'https://raw.githubusercontent.com/google/material-design-icons/master/src/';
const FONTS = { poppins_regular: 'Poppins-Regular', poppins_medium: 'Poppins-Medium', poppins_semibold: 'Poppins-SemiBold', poppins_bold: 'Poppins-Bold' };
const ICONS = {
  menu: 'navigation/menu', search: 'action/search', close: 'navigation/close', bag: 'action/shopping_bag',
  heart: 'action/favorite', heart_outline: 'action/favorite_border', near_me: 'maps/near_me', pin: 'communication/location_on',
  clock: 'action/schedule', wallet: 'action/account_balance_wallet', edit: 'image/edit', star: 'toggle/star',
  chevron_right: 'navigation/chevron_right', back: 'navigation/arrow_back', call: 'communication/call', share: 'social/share',
  directions: 'maps/directions', person: 'social/person', settings: 'action/settings', help: 'action/help_outline',
  receipt: 'action/receipt', logout: 'action/exit_to_app', camera: 'image/photo_camera', gallery: 'image/photo_library',
  add: 'content/add', remove: 'content/remove', rider: 'maps/delivery_dining', check: 'action/check_circle',
  restaurant: 'maps/restaurant', offer: 'maps/local_offer', delete: 'action/delete', info: 'action/info',
  email: 'communication/email', home: 'action/home', my_location: 'maps/my_location', lock: 'action/lock', contacts: 'communication/contacts',
};

(async () => {
  fs.mkdirSync('app/src/main/res/font', { recursive: true });
  fs.mkdirSync('app/src/main/res/drawable', { recursive: true });
  for (const [name, file] of Object.entries(FONTS)) {
    const res = await fetch(`${FONT_BASE}${file}.ttf`);
    if (!res.ok) throw new Error(`font ${file}: ${res.status}`);
    fs.writeFileSync(`app/src/main/res/font/${name}.ttf`, Buffer.from(await res.arrayBuffer()));
  }
  for (const [name, path] of Object.entries(ICONS)) {
    const res = await fetch(`${ICON_BASE}${path}/materialicons/24px.svg`);
    if (!res.ok) { console.log('MISSING', name, path, res.status); continue; }
    const svg = await res.text();
    const paths = [...svg.matchAll(/<path\b([^>]*)\/?>/g)]
      .filter((m) => !/fill="none"/.test(m[1]))
      .map((m) => /\bd="([^"]+)"/.exec(m[1])[1]);
    const body = paths.map((d) => `    <path android:fillColor="#FF000000" android:pathData="${d}" />`).join('\n');
    fs.writeFileSync(`app/src/main/res/drawable/ic_${name}.xml`,
      `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp" android:height="24dp"\n    android:viewportWidth="24" android:viewportHeight="24"\n    android:tint="?attr/colorControlNormal">\n${body}\n</vector>\n`);
  }
  console.log('assets done');
})();
