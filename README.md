# FoodRush

A food delivery app for Bangladesh, written entirely in Java (no Kotlin), with XML layouts and ViewBinding.

There are three kinds of account in one app:
- **Customer:** orders food and follows the rider live on the map.
- **Rider:** takes delivery jobs, shares their location, and delivers with the customer's PIN.
- **Restaurant:** accepts and cooks orders, and edits its own menu, photos, prices, hours and coupons.

Prices are in taka. The map, address search and road routing cover the whole of Bangladesh. Everything used is free.

## Run it on your phone

You need Android Studio and a phone running Android 7.0 or newer, plus a USB cable. The phone also needs internet.

### 1. Turn on USB debugging on the phone
1. Open **Settings > About phone**.
2. Tap **Build number** seven times, until it says you are a developer. On some brands it is under **Software information**.
3. Go back to Settings and open **Developer options** (sometimes under **System** or **Additional settings**).
4. Turn on **USB debugging**.

### 2. Open the project
1. Start Android Studio and choose **File > Open**.
2. Select the `FoodRush` folder and click **OK**. If asked, choose **Trust Project**.
3. Wait for the Gradle sync to finish. The first sync downloads libraries and can take several minutes.

### 3. Connect and run
1. Plug the phone in with USB. If the phone asks **Allow USB debugging?**, tap **Allow**.
2. Pick your phone in the device list at the top of Android Studio.
3. Click the green **Run** button (or press Shift+F10).

### If something goes wrong
| Problem | Fix |
|---|---|
| Phone is not in the device list | Try another USB cable or port, set USB mode to **File transfer**, and accept the debugging prompt. |
| "SDK platform 37 not found" | In **Tools > SDK Manager**, install **Android 17 (API 37)**. |
| Install blocked on Xiaomi, Oppo or Vivo | In Developer options, also turn on **Install via USB**. |
| Gradle says `jlink.exe does not exist` | **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**, set **Gradle JDK** to **jbr-25** (Android Studio's own). |

## Firebase (needed for riders, restaurants, chat and calls)

Without Firebase the app still works, but only as a customer: restaurants and riders are simulated on the phone.

If you already connected Firebase for version 1, you only need steps 6 and 7.

1. Go to <https://console.firebase.google.com> and create a project (Google Analytics is not needed).
2. Add an Android app with the package name `com.foodrush.app`.
3. Download `google-services.json` and put it in `FoodRush/app/`.
4. **Authentication > Sign-in method**: enable **Email/Password**.
5. **Firestore Database > Create database**: choose a location and start in production mode.
6. **Firestore Database > Rules**: replace everything with the rules below and click **Publish**.
7. In Android Studio, click **Sync Project with Gradle Files** and run the app again.

The first account to sign in uploads the starting menu (35 restaurants) to Firestore. After that, restaurant accounts edit their own menus and every phone sees the changes.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() { return request.auth != null; }
    function me() { return request.auth.uid; }
    function myRole() { return get(/databases/$(database)/documents/users/$(me())).data.role; }
    function seeding() { return !exists(/databases/$(database)/documents/meta/seed); }
    function ownsRestaurant(rid) {
      return get(/databases/$(database)/documents/restaurants/$(rid)).data.ownerUid == me();
    }
    function changedOnly(keys) {
      return request.resource.data.diff(resource.data).affectedKeys().hasOnly(keys);
    }

    match /meta/{doc} {
      allow read: if signedIn();
      allow create: if signedIn() && seeding();
    }

    match /users/{userId} {
      allow read, write: if signedIn() && me() == userId;
    }

    match /restaurants/{rid} {
      allow read: if signedIn();
      allow create: if signedIn() && (seeding() || request.resource.data.ownerUid == me());
      allow update: if signedIn() && (
        (resource.data.ownerUid == me() && request.resource.data.ownerUid == me())
        || (resource.data.ownerUid == null && changedOnly(['ownerUid']) && request.resource.data.ownerUid == me())
        || changedOnly(['rating', 'ratingCount']));
    }

    match /dishes/{dishId} {
      allow read: if signedIn();
      allow create: if signedIn() && (seeding() || ownsRestaurant(request.resource.data.restaurantId));
      allow update: if signedIn() && ownsRestaurant(resource.data.restaurantId)
        && request.resource.data.restaurantId == resource.data.restaurantId;
      allow delete: if signedIn() && ownsRestaurant(resource.data.restaurantId);
    }

    match /photos/{dishId} {
      allow read: if signedIn();
      allow create, update: if signedIn() && ownsRestaurant(request.resource.data.restaurantId);
      allow delete: if signedIn() && (resource == null || ownsRestaurant(resource.data.restaurantId));
    }

    match /coupons/{code} {
      allow read: if signedIn();
      allow create: if signedIn() && ownsRestaurant(request.resource.data.restaurantId);
      allow update: if signedIn() && ownsRestaurant(resource.data.restaurantId)
        && request.resource.data.restaurantId == resource.data.restaurantId;
      allow delete: if signedIn() && ownsRestaurant(resource.data.restaurantId);
    }

    match /reviews/{reviewId} {
      allow read, create: if signedIn();
      allow update: if signedIn() && ownsRestaurant(resource.data.restaurantId) && changedOnly(['reply']);
    }

    match /riders/{riderId} {
      allow read: if signedIn();
      allow create, update, delete: if signedIn() && me() == riderId;
      allow update: if signedIn() && changedOnly(['ratingSum', 'ratingCount']);
    }

    match /orders/{orderId} {
      function order() { return get(/databases/$(database)/documents/orders/$(orderId)).data; }
      function inOrder() {
        return order().customerUid == me() || order().riderUid == me() || me() in order().ownerUids;
      }

      allow read: if signedIn() && (resource.data.customerUid == me() || resource.data.riderUid == me()
        || me() in resource.data.ownerUids || (resource.data.riderUid == null && myRole() == 'rider'));
      allow create: if signedIn() && request.resource.data.customerUid == me();
      allow update: if signedIn() && (
        (resource.data.customerUid == me() && request.resource.data.customerUid == me()
          && (request.resource.data.riderUid == resource.data.riderUid
            || (resource.data.riderUid == null && request.resource.data.riderUid in ['sim', 'none'])))
        || (me() in resource.data.ownerUids
          && changedOnly(['kitchens', 'status', 'acceptedAt', 'readyAt', 'cancelReason', 'riderUid'])
          && (request.resource.data.riderUid == resource.data.riderUid || request.resource.data.riderUid == 'none'))
        || (resource.data.riderUid == null && myRole() == 'rider' && request.resource.data.riderUid == me()
          && changedOnly(['riderUid', 'riderName', 'assignedAt']))
        || (resource.data.riderUid == me() && request.resource.data.riderUid == me()
          && changedOnly(['kitchens', 'status', 'pickedUpAt', 'riderLat', 'riderLon', 'riderAt', 'arriving', 'deliveredAt'])));

      match /private/contact {
        allow read: if signedIn() && inOrder();
        allow create: if signedIn()
          && getAfter(/databases/$(database)/documents/orders/$(orderId)).data.customerUid == me();
        allow update: if signedIn() && order().riderUid == me();
      }
      match /private/pin {
        allow read: if signedIn() && order().customerUid == me();
        allow create: if signedIn()
          && getAfter(/databases/$(database)/documents/orders/$(orderId)).data.customerUid == me();
      }
      match /messages/{messageId} {
        allow read: if signedIn() && (order().customerUid == me() || order().riderUid == me());
        allow create: if signedIn() && request.resource.data.fromUid == me()
          && (order().customerUid == me() || order().riderUid == me());
      }
    }

    match /calls/{callId} {
      allow read: if signedIn() && (resource == null || resource.data.callerUid == me() || resource.data.calleeUid == me());
      allow create: if signedIn() && request.resource.data.callerUid == me();
      allow update, delete: if signedIn() && (resource.data.callerUid == me() || resource.data.calleeUid == me());
      match /candidates/{candidateId} {
        function call() { return get(/databases/$(database)/documents/calls/$(callId)).data; }
        allow read, create: if signedIn() && (call().callerUid == me() || call().calleeUid == me());
      }
    }
  }
}
```

### Realtime Database (riders online now)

Create it under **Build → Realtime Database** (location Singapore, locked mode), then paste these rules
in its **Rules** tab. Riders appear under `ridersOnline/{uid}` while their Online switch is on, and
Firebase removes them by itself when their phone disconnects. Customers see the live count on the home
screen.

```json
{
  "rules": {
    "ridersOnline": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid === $uid"
      }
    }
  }
}
```

## Trying every role

A full test needs two or three phones, each signed in to a different account. Create the accounts on the sign-up screen and pick the role there. Restaurant and rider accounts need Firebase.

1. **Restaurant phone:** sign up as **Restaurant**, pick your restaurant from the list (for example Kacchi Ghor in Khulna) or add a new one, then keep **Open** switched on.
2. **Rider phone:** sign up as **Rider**, allow location and notifications, and switch **Online** on.
3. **Customer phone:** sign up as **Customer**, set an address near that restaurant, order from it and pay (all payments are dummies).
4. **Restaurant:** the new order rings. Tap **Accept**, then **Start cooking**, then **Food ready**.
5. **Rider:** the job appears under **Jobs nearby**, with the quickest one by road marked **Recommended**. Tap **Accept job**, then **Picked up** at the restaurant.
6. **Customer:** watch the rider's real position move on the map, and use **Chat** or **Call**.
7. **Rider:** at the customer's door, tap **Hand over · enter PIN** and type the 4-digit PIN from the customer's screen.
8. **Customer:** rate the rider, add a tip and review the food.

You don't need every phone. If a restaurant doesn't answer within 2 minutes, or no rider takes the order within 2 minutes, the customer's phone plays that part (a "demo" restaurant or rider), so an order always completes.

### Testing tips
- A rider and a customer at the same desk are already within 300 m, so the order shows **Arriving** as soon as the food is picked up.
- **Calls** work when both phones have the app open or an order is active. Firebase's free plan has no server to wake a closed app.
- **Calls between far-away phones:** mobile networks in Bangladesh usually block direct internet calls, so in-app calls need a free TURN relay to pass the audio. Without one, calls only connect between nearby phones.
  1. Sign up at <https://www.metered.ca/stun-turn> (the free plan is enough, no card needed). In **TURN Server > Credentials**, create a credential.
  2. Add these three lines to `FoodRush/local.properties`, with your own username and password:
     ```
     turn.url=stun:stun.relay.metered.ca:80,turn:global.relay.metered.ca:80,turn:global.relay.metered.ca:80?transport=tcp,turn:global.relay.metered.ca:443,turns:global.relay.metered.ca:443?transport=tcp
     turn.username=YOUR_USERNAME
     turn.password=YOUR_PASSWORD
     ```
  3. Rebuild the app.
- **If an in-app call still can't connect** after 15 seconds, the call screen offers **Call by phone**. The phone icons on the tracking and delivery screens always open the normal phone dialer. The in-app (free) call is the icon at the top of the chat.

## Free services used

| Need | Free service | Notes |
|---|---|---|
| Map tiles | OpenStreetMap via osmdroid | Scrolling is limited to Bangladesh |
| Address search | Photon | OpenStreetMap-based, limited to Bangladesh |
| Map point to address | Android's Geocoder, then Nominatim | |
| Road data for routing | Overpass API | Only the roads around each delivery are downloaded, then cached for 30 days |
| Backup router | OSRM public server | Used if Overpass is busy |
| Accounts, orders, menus, chat | Firebase Auth and Firestore | Spark (free) plan |
| Food photos | Firestore | Shrunk to a small JPEG, because Firebase Storage needs the paid plan |
| Voice calls | WebRTC | Firestore connects the two phones; Google's free STUN server helps |
| Starting dish photos | Wikimedia Commons | Credits are in **Settings > Photo credits** |

Payments (cash, bKash, Nagad, Rocket, card) are dummies. The test card is `4242 4242 4242 4242`, any future expiry and any CVV. The wallet OTP is `123456`, and any 4 or 5 digit PIN works.

## The algorithm: Dijkstra on real roads

`route/OsmGraphParser.java` turns OpenStreetMap road data into a graph:
- Junctions become nodes, and the bends between them are kept so routes follow the street.
- One-way roads and roundabouts are respected.
- Each road type has a typical motorbike speed in Bangladeshi traffic.
- Small cut-off pieces of road are dropped.

`route/Dijkstra.java` then runs on that graph. Complexity is O((V + E) log V).

Dijkstra answers these questions:
- **Restaurant list:** riding time from every nearby restaurant to the customer, from one run over the reversed graph.
- **Checkout:** the route through every restaurant in the cart, then to the customer. It gives the delivery fee (30 tk + 10 tk per km), the ETA and the route drawn on the map.
- **Rider's job list:** riding time from the rider to every job's pickup, from one run. The quickest job is marked Recommended.

Each order shows whether its route came from Dijkstra, OSRM or a straight-line estimate.

## Restaurant data

- **Khulna district:** real restaurants, named and placed using OpenStreetMap, plus web listings for Abbas Hotel, Shawarma House and Kababz. Their menus and prices are typical local ones.
- **Other cities:** invented restaurants in real neighbourhoods.
- Restaurant accounts can change all of this in the app.

## How the course topics are used

| Topic | Where |
|---|---|
| Project structure, Gradle, manifest | Packages `ui` (with `ui.restaurant`, `ui.rider`), `adapter`, `model`, `data`, `order`, `network`, `route`, `call`, `service`, `receiver`, `provider`, `view`, `util` |
| Activity lifecycle | All seven callbacks logged in `ui/BaseActivity`; state kept with `onSaveInstanceState` |
| Explicit and implicit intents | Screen navigation with extras; dial, `geo:` maps hand-off, share, email, SMS, camera, gallery, contacts, app settings |
| Getting a result back | Address, payment, category and restaurant-location pickers with `registerForActivityResult` |
| Click handling | XML `android:onClick` (Help), Activity as `OnClickListener` (Profile), anonymous class (Login), lambdas, adapter callbacks |
| UI layouts | ConstraintLayout, LinearLayout, FrameLayout, RelativeLayout (`item_order`), TableLayout (cart totals), GridView (categories), ListView (FAQ, address results), WebView, CoordinatorLayout (restaurant) |
| RecyclerView and custom views | Many adapters, several view types in `RestaurantPageAdapter`, chat bubbles; `RatingBarView`, `StatusStepperView`, `QuantityStepperView` |
| Services | `OrderTrackingService` (foreground and bound), `RiderService` (foreground, location), `KitchenService` (foreground), `FoodRushMessagingService` |
| Broadcast receivers | Dynamic `ConnectivityReceiver`, static `BootReceiver`, custom `OrderStatusReceiver` |
| Content providers | `FoodRushProvider` over SQLite (cart, favourites, searches, orders, reviews) with `ContentObserver`s; the Contacts provider; images through `ContentResolver` |
| Notifications | Channels, `PendingIntent`, `RemoteViews`, action buttons, `MessagingStyle` chat, `CallStyle` incoming calls with a full-screen intent |
| Permissions | Location, camera, photos, microphone and notifications, with rationale and a route to settings |
| HTTP and JSON | Retrofit and Gson (Nominatim, OSRM); `HttpURLConnection` and `org.json` (Photon search in `network/SimpleHttp`); OkHttp (Overpass) |
| XML parsing | `XmlPullParser` on the bundled menu and promotions, and on OpenStreetMap road data |
| Database | `data/DbHelper` (SQLite, `onUpgrade` to version 3) |
| Background work | `ExecutorService` in `util/AppExecutors`, WorkManager `PromoRefreshWorker` |
| Advanced APIs | Fused location, Geocoder, osmdroid maps, Firebase Cloud Messaging, WebRTC |
| Firebase | Auth; Firestore with transactions, live listeners, subcollections, batched writes and security rules |

## Tests

From the project folder, run `gradlew testDebugUnitTest`, or right-click `app/src/test` in Android Studio and choose **Run Tests**.

The tests cover:
- the order state machine
- road parsing and Dijkstra
- taka pricing and VAT
- Bangladeshi phone numbers and borders
- the bundled data
