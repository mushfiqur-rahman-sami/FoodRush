// Downloads the bundled dish photos from Wikimedia Commons (free licences) and writes the credits page.
// Run from the project root: node tools/fetch_dish_photos.js
// Photos land in app/src/main/res/drawable-nodpi; credits in app/src/main/assets/photo_credits.html.

const fs = require('fs');
const path = require('path');

const UA = 'FoodRushStudentProject/1.0 (Android course project)';
const WIDTH = 420;
const OUT = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const CREDITS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'photo_credits.html');

const PHOTOS = {
  dish_kacchi: 'File:Kacchi Biryani.jpg',
  dish_chicken_biryani: 'File:Chicken Biryani – Bangla Wikipedia edit-a-thon at Bishwo Shahitto Kendro, June 2022.jpg',
  dish_tehari: 'File:Tehari (2).jpg',
  dish_morog_polao: 'File:Morog Polao 3.jpg',
  dish_khichuri: 'File:Bhuna Khichuri with Chicken, Vegetables, Chili, and Lemon.jpg',
  dish_chui_beef: 'File:গরুর চুইঝাল.jpg',
  dish_chui_mutton: 'File:খাসির চুইঝাল.jpg',
  dish_mezban: 'File:Mejban Beef and Rice-Chittagong.jpg',
  dish_kala_bhuna: 'File:Kala bhuna 1.jpg',
  dish_rezala: 'File:Mutton Rezala bengali style.JPG',
  dish_shorshe_ilish: 'File:Shorshe Ilish Maach.jpg',
  dish_hilsa_fry: 'File:Hilsa Fry.jpg',
  dish_chingri: 'File:Chingri Macher Malai Curry.jpg',
  dish_rui: "File:Rohu fish curry ('Rui maach' in Bengali).JPG",
  dish_bhorta: 'File:Homemade tomato bhorta from Bangladesh.jpg',
  dish_rice_dal: 'File:Plain rice with egg curry dal bhorta & bitter melon fry.jpg',
  dish_tikka: 'File:Chicken Tikka Kebab.jpg',
  dish_seekh: 'File:Indian Chicken Seekh Kebab.jpg',
  dish_shami: 'File:Shami kebabs.jpg',
  dish_tandoori: 'File:Grilled Chicken Tandoor.jpg',
  dish_shawarma: 'File:Chicken Shawarma Wrap - Lavash 2024-09-11.jpg',
  dish_naan: 'File:Naan Bread.JPG',
  dish_paratha: 'File:Paratha is a dough fried flatbread native to India and Pakistan.jpg',
  dish_fried_rice: 'File:Fried rice with chicken and egg.jpg',
  dish_chowmein: 'File:Chicken Chow mein 1.jpg',
  dish_thai_soup: 'File:Tom Yum mixed with clear soup.jpg',
  dish_chili_chicken: 'File:Chicken Chilli 1.jpg',
  dish_fried_chicken: 'File:Fried-Chicken-Set.jpg',
  dish_burger: "File:Original Beef Burger - Martin's Burger House.jpg",
  dish_pizza: 'File:Vegetarian Pizza.jpg',
  dish_fries: 'File:Plate of chips at the Chalet Cafe, Cowfold, West Sussex, England.jpg',
  dish_fuchka: 'File:Crispy Pani Puri.jpg',
  dish_chotpoti: 'File:Chotpoti.jpg',
  dish_singara: 'File:Singara with sliced onions .jpg',
  dish_samosa: 'File:Samosa with sweet chutney.jpg',
  dish_jhalmuri: 'File:Bangladeshi Jhalmuri 20220331.jpg',
  dish_haleem: 'File:Haleem 02.jpg',
  dish_roshogolla: 'File:Bengali Sponge Roshogolla in date palm jaggery syrup.jpg',
  dish_mishti_doi: 'File:Mishti doi (Mitha Dahi).jpg',
  dish_payesh: 'File:Kheer.jpg',
  dish_falooda: 'File:Falooda of Euro Hut, Dhaka.jpg',
  dish_borhani: 'File:A Glass of Borhani.jpg',
  dish_lassi: 'File:Lassi 1.jpg',
  dish_tea: 'File:Cup of Milk Tea.jpg',
  dish_pitha: 'File:Bhapa Pitha ভাপা পিঠা.jpg',
  dish_cake: 'File:Piece of chocolate cake on a white plate decorated with chocolate sauce.jpg',
  dish_firni: 'File:Firni Or Phirni Bangladeshi Sweet (1).jpg',
  dish_chicken_roast: 'File:Roasted Chicken - Howrah 2013-11-01 3983.JPG',
  dish_mutton_curry: 'File:Mutton Curry 1.jpg',
  dish_chicken_curry: 'File:Bengali chicken curry 2.jpg',
  dish_beef_curry: 'File:Kerala Beef Curry.jpg',
  dish_egg_curry: 'File:Egg curry pic.jpg',
};

const strip = (html) => String(html || '').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();
const escape = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function fetchWithRetry(url) {
  for (let attempt = 1; attempt <= 4; attempt++) {
    const response = await fetch(url, {headers: {'User-Agent': UA}});
    if (response.ok) {
      return response;
    }
    await sleep(attempt * 3000);
  }
  throw new Error('Failed: ' + url);
}

(async () => {
  fs.mkdirSync(OUT, {recursive: true});
  fs.mkdirSync(path.dirname(CREDITS), {recursive: true});
  const rows = [];
  for (const [key, title] of Object.entries(PHOTOS)) {
    const api = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo'
      + '&iiprop=url|extmetadata&iiurlwidth=' + WIDTH + '&titles=' + encodeURIComponent(title);
    const info = Object.values((await (await fetchWithRetry(api)).json()).query.pages)[0].imageinfo[0];
    const meta = info.extmetadata || {};
    const target = path.join(OUT, key + '.jpg');
    if (!fs.existsSync(target)) {
      const bytes = Buffer.from(await (await fetchWithRetry(info.thumburl)).arrayBuffer());
      fs.writeFileSync(target, bytes);
      await sleep(400);
    }
    rows.push({title: title.replace(/^File:/, ''), page: info.descriptionurl,
      author: strip(meta.Artist && meta.Artist.value) || 'Unknown',
      license: strip(meta.LicenseShortName && meta.LicenseShortName.value) || 'See file page'});
    console.log('ok', key);
  }
  const items = rows.map((r) => '<li><a href="' + r.page + '">' + escape(r.title) + '</a><br>'
    + escape(r.author) + ' · ' + escape(r.license) + '</li>').join('\n');
  fs.writeFileSync(CREDITS, '<!doctype html><html><head><meta charset="utf-8">'
    + '<meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<style>body{font-family:sans-serif;padding:16px;line-height:1.5;color:#1f1f1f}li{margin-bottom:10px}'
    + 'a{color:#b07a1c}</style></head><body><h2>Photo credits</h2>'
    + '<p>Dish photos come from Wikimedia Commons and are used under the licences shown. '
    + 'Restaurants can replace them with their own photos.</p><ul>\n' + items + '\n</ul></body></html>\n');
  console.log('credits written:', rows.length);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
