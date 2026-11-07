package com.foodrush.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One delivery. The same map shape is stored in Firestore (orders/{id}) and, as JSON, in the local
 * SQLite table behind the content provider. Money is whole taka.
 */
public class Order {

    public static final int CANCELLED = -1;
    public static final int PLACED = 0;
    public static final int ACCEPTED = 1;
    public static final int PREPARING = 2;
    public static final int READY = 3;
    public static final int PICKED_UP = 4;
    public static final int ARRIVING = 5;
    public static final int DELIVERED = 6;

    /** riderUid of the built-in demo rider, used when no real rider takes the order in time. */
    public static final String RIDER_SIM = "sim";
    /** riderUid of a cancelled order nobody delivered, so riders' "unassigned" query skips it. */
    public static final String RIDER_NONE = "none";

    /** One restaurant's part of the order. */
    /** One cart line, kept so restaurants can count best sellers and customers can reorder. */
    public static class Line {
        public String dishId;
        public String name;
        public String restaurantId;
        public int quantity;
        public int unitPrice;
        public String size = "";
        public String extras = "";
        public int prepMinutes;

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("dishId", dishId);
            m.put("name", name);
            m.put("restaurantId", restaurantId);
            m.put("quantity", quantity);
            m.put("unitPrice", unitPrice);
            m.put("size", size);
            m.put("extras", extras);
            m.put("prepMinutes", prepMinutes);
            return m;
        }

        static Line from(Map<String, Object> m) {
            Line l = new Line();
            l.dishId = Fields.text(m, "dishId");
            l.name = Fields.text(m, "name");
            l.restaurantId = Fields.text(m, "restaurantId");
            l.quantity = Fields.integer(m, "quantity", 1);
            l.unitPrice = Fields.integer(m, "unitPrice", 0);
            l.size = Fields.text(m, "size");
            l.extras = Fields.text(m, "extras");
            l.prepMinutes = Fields.integer(m, "prepMinutes", 15);
            return l;
        }
    }

    public static class Kitchen {
        public static final int REJECTED = -1;
        public static final int NEW = 0;
        public static final int ACCEPTED = 1;
        public static final int PREPARING = 2;
        public static final int READY = 3;

        public String restaurantId;
        public String name;
        public double lat;
        public double lon;
        public String phone = "";
        public String ownerUid;
        public int status = NEW;
        public boolean pickedUp;
        public int subtotal;
        public int prepMinutes;
        /** When the customer's phone took over cooking for an unanswered kitchen; 0 if a person runs it. */
        public long simStartedAt;
        public long readyAt;

        public LatLon location() {
            return new LatLon(lat, lon);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("restaurantId", restaurantId);
            m.put("name", name);
            m.put("lat", lat);
            m.put("lon", lon);
            m.put("phone", phone);
            m.put("ownerUid", ownerUid);
            m.put("status", status);
            m.put("pickedUp", pickedUp);
            m.put("subtotal", subtotal);
            m.put("prepMinutes", prepMinutes);
            m.put("simStartedAt", simStartedAt);
            m.put("readyAt", readyAt);
            return m;
        }

        static Kitchen from(Map<String, Object> m) {
            Kitchen k = new Kitchen();
            k.restaurantId = Fields.text(m, "restaurantId");
            k.name = Fields.text(m, "name");
            k.lat = Fields.decimal(m, "lat");
            k.lon = Fields.decimal(m, "lon");
            k.phone = Fields.text(m, "phone");
            k.ownerUid = Fields.nullableText(m, "ownerUid");
            k.status = Fields.integer(m, "status", NEW);
            k.pickedUp = Fields.flag(m, "pickedUp", false);
            k.subtotal = Fields.integer(m, "subtotal", 0);
            k.prepMinutes = Fields.integer(m, "prepMinutes", 15);
            k.simStartedAt = Fields.number(m, "simStartedAt");
            k.readyAt = Fields.number(m, "readyAt");
            return k;
        }
    }

    public String id;
    public String customerUid;
    public String customerName = "";
    public long placedAt;
    /** Human-readable lines such as "2 × Kacchi Biryani (Full) · Kacchi Ghor". */
    public String items = "";
    public int itemCount;
    public int subtotal;
    public int discount;
    public int vat;
    public int fee;
    public int total;
    public int tip;
    public String coupon = "";
    public String payment = "";
    public String address = "";
    public double lat;
    public double lon;
    public List<Kitchen> kitchens = new ArrayList<>();
    public List<Line> lines = new ArrayList<>();
    /** When the customer wants the food; 0 for as soon as possible. */
    public long scheduledFor;
    public List<String> restaurantIds = new ArrayList<>();
    /** Owners of the kitchens at order time; lets restaurant accounts query and update their orders. */
    public List<String> ownerUids = new ArrayList<>();
    public int status = PLACED;
    public String cancelReason = "";
    public String riderUid;
    public String riderName = "";
    public double riderLat;
    public double riderLon;
    public long riderAt;
    public boolean arriving;
    /** Planned road route, "lat,lon;lat,lon;...", pickups first then the customer. */
    public String route = "";
    /** Restaurant ids in the order the rider visits them. */
    public List<String> pickupOrder = new ArrayList<>();
    public double travelMinutes;
    public double distanceKm;
    public int etaMinutes;
    /** How the route was found: "dijkstra", "osrm" or "estimate". */
    public String routeSource = "";
    /** SHA-256 of id + PIN; the PIN itself is only on the customer's side. */
    public String pinHash = "";
    public long acceptedAt;
    public long readyAt;
    public long assignedAt;
    public long pickedUpAt;
    public long deliveredAt;
    public int riderRating;
    public String riderComment = "";
    public boolean reviewed;

    public LatLon customer() {
        return new LatLon(lat, lon);
    }

    /** Cash the rider collects at the door. */
    public boolean isCashOnDelivery() {
        return payment != null && payment.startsWith("Cash");
    }

    public boolean isActive() {
        return status != CANCELLED && status != DELIVERED;
    }

    public boolean hasRealRider() {
        return riderUid != null && !RIDER_SIM.equals(riderUid) && !RIDER_NONE.equals(riderUid);
    }

    public boolean hasSimRider() {
        return RIDER_SIM.equals(riderUid);
    }

    public Kitchen kitchen(String restaurantId) {
        for (Kitchen k : kitchens) {
            if (k.restaurantId.equals(restaurantId)) {
                return k;
            }
        }
        return null;
    }

    public List<LatLon> routePoints() {
        List<LatLon> points = new ArrayList<>();
        if (route == null || route.isEmpty()) {
            return points;
        }
        for (String part : route.split(";")) {
            if (!part.isEmpty()) {
                points.add(LatLon.decode(part));
            }
        }
        return points;
    }

    public static String encodeRoute(List<LatLon> points) {
        StringBuilder sb = new StringBuilder();
        for (LatLon point : points) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(point.encode());
        }
        return sb.toString();
    }

    public String shortId() {
        return id.length() > 8 ? id.substring(0, 8).toUpperCase(java.util.Locale.US) : id;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("customerUid", customerUid);
        m.put("customerName", customerName);
        m.put("placedAt", placedAt);
        m.put("items", items);
        m.put("itemCount", itemCount);
        m.put("subtotal", subtotal);
        m.put("discount", discount);
        m.put("vat", vat);
        m.put("fee", fee);
        m.put("total", total);
        m.put("tip", tip);
        m.put("coupon", coupon);
        m.put("payment", payment);
        m.put("address", address);
        m.put("lat", lat);
        m.put("lon", lon);
        List<Map<String, Object>> kitchenMaps = new ArrayList<>();
        for (Kitchen k : kitchens) {
            kitchenMaps.add(k.toMap());
        }
        m.put("kitchens", kitchenMaps);
        m.put("restaurantIds", restaurantIds);
        m.put("ownerUids", ownerUids);
        m.put("status", status);
        m.put("cancelReason", cancelReason);
        m.put("riderUid", riderUid);
        m.put("riderName", riderName);
        m.put("riderLat", riderLat);
        m.put("riderLon", riderLon);
        m.put("riderAt", riderAt);
        m.put("arriving", arriving);
        m.put("route", route);
        m.put("pickupOrder", pickupOrder);
        m.put("travelMinutes", travelMinutes);
        m.put("distanceKm", distanceKm);
        m.put("etaMinutes", etaMinutes);
        m.put("routeSource", routeSource);
        m.put("pinHash", pinHash);
        m.put("acceptedAt", acceptedAt);
        m.put("readyAt", readyAt);
        m.put("assignedAt", assignedAt);
        m.put("pickedUpAt", pickedUpAt);
        m.put("deliveredAt", deliveredAt);
        m.put("riderRating", riderRating);
        m.put("reviewed", reviewed);
        m.put("riderComment", riderComment);
        m.put("scheduledFor", scheduledFor);
        List<Map<String, Object>> lineMaps = new ArrayList<>();
        for (Line l : lines) {
            lineMaps.add(l.toMap());
        }
        m.put("lines", lineMaps);
        return m;
    }

    public static Order from(Map<String, Object> m) {
        Order o = new Order();
        o.id = Fields.text(m, "id");
        o.customerUid = Fields.text(m, "customerUid");
        o.customerName = Fields.text(m, "customerName");
        o.placedAt = Fields.number(m, "placedAt");
        o.items = Fields.text(m, "items");
        o.itemCount = Fields.integer(m, "itemCount", 0);
        o.subtotal = Fields.integer(m, "subtotal", 0);
        o.discount = Fields.integer(m, "discount", 0);
        o.vat = Fields.integer(m, "vat", 0);
        o.fee = Fields.integer(m, "fee", 0);
        o.total = Fields.integer(m, "total", 0);
        o.tip = Fields.integer(m, "tip", 0);
        o.coupon = Fields.text(m, "coupon");
        o.payment = Fields.text(m, "payment");
        o.address = Fields.text(m, "address");
        o.lat = Fields.decimal(m, "lat");
        o.lon = Fields.decimal(m, "lon");
        for (Map<String, Object> k : Fields.maps(m, "kitchens")) {
            o.kitchens.add(Kitchen.from(k));
        }
        o.restaurantIds = Fields.strings(m, "restaurantIds");
        o.ownerUids = Fields.strings(m, "ownerUids");
        o.status = Fields.integer(m, "status", PLACED);
        o.cancelReason = Fields.text(m, "cancelReason");
        o.riderUid = Fields.nullableText(m, "riderUid");
        o.riderName = Fields.text(m, "riderName");
        o.riderLat = Fields.decimal(m, "riderLat");
        o.riderLon = Fields.decimal(m, "riderLon");
        o.riderAt = Fields.number(m, "riderAt");
        o.arriving = Fields.flag(m, "arriving", false);
        o.route = Fields.text(m, "route");
        o.pickupOrder = Fields.strings(m, "pickupOrder");
        o.travelMinutes = Fields.decimal(m, "travelMinutes");
        o.distanceKm = Fields.decimal(m, "distanceKm");
        o.etaMinutes = Fields.integer(m, "etaMinutes", 0);
        o.routeSource = Fields.text(m, "routeSource");
        o.pinHash = Fields.text(m, "pinHash");
        o.acceptedAt = Fields.number(m, "acceptedAt");
        o.readyAt = Fields.number(m, "readyAt");
        o.assignedAt = Fields.number(m, "assignedAt");
        o.pickedUpAt = Fields.number(m, "pickedUpAt");
        o.deliveredAt = Fields.number(m, "deliveredAt");
        o.riderRating = Fields.integer(m, "riderRating", 0);
        o.reviewed = Fields.flag(m, "reviewed", false);
        o.riderComment = Fields.text(m, "riderComment");
        o.scheduledFor = Fields.number(m, "scheduledFor");
        for (Map<String, Object> l : Fields.maps(m, "lines")) {
            o.lines.add(Line.from(l));
        }
        return o;
    }

    public Order copy() {
        return from(toMap());
    }
}
