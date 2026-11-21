package org.example.newdivarextraction.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.dto.AttributeEntry;
import org.example.newdivarextraction.dto.PropertyDto;
import org.example.newdivarextraction.model.PropertyListing;
import org.example.newdivarextraction.repository.PropertyListingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final PropertyListingRepository propertyListingRepository;
    private final Gson gson = new Gson();

    @GetMapping("/listings")
    public ResponseEntity<Map<String, Object>> searchListings(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) List<String> districts,
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Long minDeposit,
            @RequestParam(required = false) Long maxDeposit,
            @RequestParam(required = false) Long minRent,
            @RequestParam(required = false) Long maxRent,
            @RequestParam(required = false) Long minArea,
            @RequestParam(required = false) Long maxArea,
            @RequestParam(required = false) Integer rooms,
            @RequestParam(required = false) Boolean hasElevator,
            @RequestParam(required = false) Boolean hasParking,
            @RequestParam(required = false) Boolean hasStorage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scrapedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        boolean needsAttributeFilter = minPrice != null || maxPrice != null
                || minDeposit != null || maxDeposit != null
                || minRent != null || maxRent != null
                || minArea != null || maxArea != null
                || rooms != null || hasElevator != null || hasParking != null || hasStorage != null;

        boolean needsDistrictFilter = districts != null && !districts.isEmpty();

        Pageable pageable = PageRequest.of(page, (needsAttributeFilter || needsDistrictFilter) ? 500 : size, sort);

        Page<PropertyListing> listingsPage = propertyListingRepository.searchListingsFull(
                city, listingType, description, pageable);

        List<Map<String, Object>> content = listingsPage.getContent().stream()
                .filter(listing -> !needsDistrictFilter || districts.stream()
                        .anyMatch(d -> d.equalsIgnoreCase(listing.getDistrict())))
                .filter(listing -> !needsAttributeFilter || matchesAttributeFilters(
                        listing, listingType, minPrice, maxPrice, minDeposit, maxDeposit, minRent, maxRent,
                        minArea, maxArea, rooms,
                        hasElevator, hasParking, hasStorage))
                .map(this::convertToMap)
                .collect(Collectors.toList());

        if (needsAttributeFilter || needsDistrictFilter) {
            int start = page * size;
            int end = Math.min(start + size, content.size());
            int totalFiltered = content.size();
            content = content.subList(start, end);

            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("totalPages", (int) Math.ceil((double) totalFiltered / size));
            result.put("totalElements", (long) totalFiltered);
            result.put("currentPage", page);
            result.put("pageSize", size);
            return ResponseEntity.ok(result);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalPages", listingsPage.getTotalPages());
        result.put("totalElements", listingsPage.getTotalElements());
        result.put("currentPage", listingsPage.getNumber());
        result.put("pageSize", listingsPage.getSize());

        return ResponseEntity.ok(result);
    }

    private boolean matchesAttributeFilters(PropertyListing listing, String listingType,
            Long minPrice, Long maxPrice, Long minDeposit, Long maxDeposit,
            Long minRent, Long maxRent, Long minArea, Long maxArea,
            Integer rooms, Boolean hasElevator, Boolean hasParking, Boolean hasStorage) {

        if (listing.getAttributes() == null) return true;
        List<AttributeEntry> attrs = PropertyDto.parseAttributes(listing.getAttributes());

        boolean isRent = "اجاره".equals(listingType);
        boolean isSale = "فروش".equals(listingType);

        if (minPrice != null || maxPrice != null) {
            if (isRent) {
                String depositStr = getAttrValue(attrs, "deposit");
                if (depositStr != null) {
                    long deposit = parseNumber(depositStr);
                    if (deposit >= 0) {
                        if (minPrice != null && deposit < minPrice) return false;
                        if (maxPrice != null && deposit > maxPrice) return false;
                    }
                }
            } else if (isSale) {
                String priceStr = getAttrValue(attrs, "price");
                if (priceStr != null) {
                    long price = parseNumber(priceStr);
                    if (price >= 0) {
                        if (minPrice != null && price < minPrice) return false;
                        if (maxPrice != null && price > maxPrice) return false;
                    }
                }
            } else {
                String priceStr = getAttrValue(attrs, "price");
                String depositStr = getAttrValue(attrs, "deposit");
                boolean matched = false;
                if (priceStr != null) {
                    long price = parseNumber(priceStr);
                    if (price >= 0) {
                        if (minPrice != null && price < minPrice) return false;
                        if (maxPrice != null && price > maxPrice) return false;
                        matched = true;
                    }
                }
                if (!matched && depositStr != null) {
                    long deposit = parseNumber(depositStr);
                    if (deposit >= 0) {
                        if (minPrice != null && deposit < minPrice) return false;
                        if (maxPrice != null && deposit > maxPrice) return false;
                    }
                }
            }
        }

        if (minDeposit != null || maxDeposit != null) {
            String depositStr = getAttrValue(attrs, "deposit");
            if (depositStr != null) {
                long deposit = parseNumber(depositStr);
                if (deposit >= 0) {
                    if (minDeposit != null && deposit < minDeposit) return false;
                    if (maxDeposit != null && deposit > maxDeposit) return false;
                }
            }
        }

        if (minRent != null || maxRent != null) {
            String rentStr = getAttrValue(attrs, "monthlyRent");
            if (rentStr != null) {
                long rent = parseNumber(rentStr);
                if (rent >= 0) {
                    if (minRent != null && rent < minRent) return false;
                    if (maxRent != null && rent > maxRent) return false;
                }
            }
        }

        if (minArea != null || maxArea != null) {
            String areaStr = getAttrValue(attrs, "area");
            if (areaStr != null) {
                long area = parseNumber(areaStr);
                if (area >= 0) {
                    if (minArea != null && area < minArea) return false;
                    if (maxArea != null && area > maxArea) return false;
                }
            }
        }

        if (rooms != null) {
            String roomsStr = getAttrValue(attrs, "rooms");
            if (roomsStr != null) {
                long r = parseNumber(roomsStr);
                if (rooms == 5) {
                    if (r < 5) return false;
                } else {
                    if (r != rooms.intValue()) return false;
                }
            } else return false;
        }

        if (hasElevator != null) {
            String val = getAttrValue(attrs, "elevator");
            boolean has = "دارد".equals(val);
            if (hasElevator && !has) return false;
        }

        if (hasParking != null) {
            String val = getAttrValue(attrs, "parking");
            boolean has = "دارد".equals(val);
            if (hasParking && !has) return false;
        }

        if (hasStorage != null) {
            String val = getAttrValue(attrs, "storage");
            boolean has = "دارد".equals(val);
            if (hasStorage && !has) return false;
        }

        return true;
    }

    private String getAttrValue(List<AttributeEntry> attrs, String key) {
        return attrs.stream()
                .filter(a -> key.equals(a.getKey()))
                .map(AttributeEntry::getValue)
                .findFirst().orElse(null);
    }

    private long parseNumber(String s) {
        if (s == null) return -1;
        String cleaned = s.replaceAll("[^0-9۰-۹٠-٩]", "");
        if (cleaned.isEmpty()) return -1;
        cleaned = cleaned.replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3")
                         .replace("۴","4").replace("۵","5").replace("۶","6").replace("۷","7")
                         .replace("۸","8").replace("۹","9")
                         .replace("٠","0").replace("١","1").replace("٢","2").replace("٣","3")
                         .replace("٤","4").replace("٥","5").replace("٦","6").replace("٧","7")
                         .replace("٨","8").replace("٩","9");
        try { return Long.parseLong(cleaned); } catch (Exception e) { return -1; }
    }

    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("cities", propertyListingRepository.findDistinctCities());
        filters.put("districts", propertyListingRepository.findDistinctDistricts());
        filters.put("listingTypes", propertyListingRepository.findDistinctListingTypes());
        filters.put("propertyTypes", propertyListingRepository.findDistinctPropertyTypes());
        return ResponseEntity.ok(filters);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", propertyListingRepository.countPersonal());
        stats.put("forSale", propertyListingRepository.countForSale());
        stats.put("forRent", propertyListingRepository.countForRent());
        stats.put("personal", propertyListingRepository.countByIsPersonalTrue());
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/listings")
    public ResponseEntity<Map<String, Object>> createPersonalListing(@RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.getOrDefault("title", "");
            String city = (String) body.getOrDefault("city", "");
            String district = (String) body.getOrDefault("district", "");
            String description = (String) body.getOrDefault("description", "");
            String listingType = (String) body.getOrDefault("listingType", "شخصی");
            String url = (String) body.getOrDefault("url", "");

            if (title.isBlank() || city.isBlank() || district.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "عنوان، شهر و منطقه الزامی هستند"));
            }

            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

            List<Map<String, String>> rawAttrs = new ArrayList<>();
            if (body.containsKey("attributes") && body.get("attributes") instanceof List) {
                rawAttrs = (List<Map<String, String>>) body.get("attributes");
            }

            List<AttributeEntry> attributes = new ArrayList<>();
            int order = 1;
            attributes.add(AttributeEntry.builder().key("title").title("عنوان").value(title).order(order++).build());

            String[] attrKeys = {"price", "deposit", "monthlyRent", "area", "rooms", "floor", "yearBuilt",
                    "totalFloors", "buildingDirection", "address"};
            String[] attrTitles = {"قیمت کل", "ودیعه", "اجاره ماهانه", "متراژ", "اتاق", "طبقه",
                    "سال ساخت", "تعداد کل طبقات", "جهت ساختمان", "آدرس"};

            for (int i = 0; i < attrKeys.length; i++) {
                for (Map<String, String> ra : rawAttrs) {
                    if (attrKeys[i].equals(ra.get("key")) && ra.get("value") != null && !ra.get("value").isBlank()) {
                        attributes.add(AttributeEntry.builder().key(attrKeys[i]).title(attrTitles[i]).value(ra.get("value")).order(order++).build());
                        break;
                    }
                }
            }

            if (!description.isBlank()) {
                attributes.add(AttributeEntry.builder().key("fullDescription").title("توضیحات").value(description).order(order++).build());
            }
            if (!url.isBlank()) {
                attributes.add(AttributeEntry.builder().key("url").title("لینک").value(url).order(order++).build());
            }

            List<String> imageUrls = new ArrayList<>();
            if (body.containsKey("imageUrls") && body.get("imageUrls") instanceof List) {
                imageUrls = (List<String>) body.get("imageUrls");
            }

            PropertyListing listing = PropertyListing.builder()
                    .token(token)
                    .title(title)
                    .description(description.isBlank() ? null : description)
                    .url(url.isBlank() ? "#" : url)
                    .listingType(listingType)
                    .imageUrls(gson.toJson(imageUrls))
                    .city(city)
                    .district(district)
                    .propertyType("apartment")
                    .isBroker(false)
                    .isSent(false)
                    .isPersonal(true)
                    .attributes(gson.toJson(attributes))
                    .sourceUrlTitle(null)
                    .build();

            propertyListingRepository.save(listing);

            Map<String, Object> result = convertToMap(listing);
            result.put("message", "فایل شخصی با موفقیت ایجاد شد");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error creating personal listing", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "خطا در ایجاد فایل"));
        }
    }

    @GetMapping("/listings/{id}")
    public ResponseEntity<Map<String, Object>> getListing(@PathVariable Long id) {
        return propertyListingRepository.findById(id)
                .map(listing -> ResponseEntity.ok(convertToMap(listing)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/listings/{id}")
    public ResponseEntity<Map<String, Object>> updateListing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return propertyListingRepository.findById(id).map(listing -> {
            if (body.containsKey("title")) {
                listing.setTitle((String) body.get("title"));
            }
            if (body.containsKey("description")) {
                listing.setDescription((String) body.get("description"));
            }
            if (body.containsKey("city")) {
                listing.setCity((String) body.get("city"));
            }
            if (body.containsKey("district")) {
                listing.setDistrict((String) body.get("district"));
            }
            if (body.containsKey("listingType")) {
                listing.setListingType((String) body.get("listingType"));
            }
            if (body.containsKey("attributes")) {
                String attrsJson = gson.toJson(body.get("attributes"));
                listing.setAttributes(attrsJson);
            }
            propertyListingRepository.save(listing);
            return ResponseEntity.ok(convertToMap(listing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Map<String, Object>> deleteListing(@PathVariable Long id) {
        if (propertyListingRepository.existsById(id)) {
            propertyListingRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/listings")
    public ResponseEntity<Map<String, Object>> deleteMultiple(
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            propertyListingRepository.deleteAllById(ids);
            return ResponseEntity.ok(Map.of("status", "deleted", "count", ids.size()));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "ids is required"));
    }

    private Map<String, Object> convertToMap(PropertyListing listing) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", listing.getId());
        map.put("token", listing.getToken());
        map.put("title", listing.getTitle());
        map.put("description", listing.getDescription());
        map.put("url", listing.getUrl());
        map.put("listingType", listing.getListingType());
        map.put("city", listing.getCity());
        map.put("district", listing.getDistrict());
        map.put("propertyType", listing.getPropertyType());
        map.put("isBroker", listing.getIsBroker());
        map.put("isSent", listing.getIsSent());
        map.put("isPersonal", listing.getIsPersonal());
        map.put("scrapedAt", listing.getScrapedAt());
        map.put("sentAt", listing.getSentAt());
        map.put("sourceUrlTitle", listing.getSourceUrlTitle());

        // Parse imageUrls
        List<String> imageUrls = new ArrayList<>();
        if (listing.getImageUrls() != null && !listing.getImageUrls().isBlank()) {
            try {
                imageUrls = gson.fromJson(listing.getImageUrls(),
                        new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            } catch (Exception e) {
                imageUrls = new ArrayList<>();
            }
        }
        map.put("imageUrls", imageUrls);

        // Parse attributes
        List<AttributeEntry> attributes = PropertyDto.parseAttributes(listing.getAttributes());
        map.put("attributes", attributes);

        // Extract key attributes for quick access
        Map<String, String> keyAttrs = new LinkedHashMap<>();
        for (AttributeEntry attr : attributes) {
            if (!attr.getKey().equals("fullDescription") && !attr.getKey().equals("title")
                    && !attr.getKey().equals("url") && attr.getValue() != null) {
                keyAttrs.put(attr.getKey(), attr.getValue());
            }
        }
        map.put("keyAttributes", keyAttrs);

        // Jalali date
        if (listing.getScrapedAt() != null) {
            map.put("scrapedAtJalali", toJalaliString(listing.getScrapedAt()));
        }

        return map;
    }

    private String toJalaliString(LocalDateTime ldt) {
        try {
            ZonedDateTime zdt = ldt.atZone(ZoneId.of("Asia/Tehran"));
            int gy = zdt.getYear();
            int gm = zdt.getMonthValue();
            int gd = zdt.getDayOfMonth();

            int[] jalali = gregorianToJalali(gy, gm, gd);
            String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"};
            return jalali[2] + " " + months[jalali[1] - 1] + " " + jalali[0];
        } catch (Exception e) {
            return ldt.toString();
        }
    }

    private int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] g_d_m = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100)
                + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + (days / 31);
            jd = 1 + (days % 31);
        } else {
            jm = 7 + ((days - 186) / 30);
            jd = 1 + ((days - 186) % 30);
        }
        return new int[]{jy, jm, jd};
    }
}
