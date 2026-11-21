package org.example.newdivarextraction.service;

import com.google.gson.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.config.DivarConfig;
import org.example.newdivarextraction.dto.AttributeEntry;
import org.example.newdivarextraction.dto.PropertyDto;
import org.example.newdivarextraction.model.PropertyListing;
import org.example.newdivarextraction.repository.PropertyListingRepository;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DivarScraperService {

    private final DivarConfig divarConfig;
    private final PropertyListingRepository propertyListingRepository;
    private final FilterService filterService;
    private final RestTemplate restTemplate;

    private static final String DIVAR_API_BASE = "https://api.divar.ir/v8/posts-v2/web/";

    private WebDriver createDriver() {
        System.setProperty("webdriver.chrome.driver", "D:\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-sync");
        options.addArguments("--no-first-run");
        options.addArguments("--disable-default-apps");
        options.addArguments("--js-flags=--max-old-space-size=256");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");

        WebDriver wd = new ChromeDriver(options);
        log.info("Selenium WebDriver initialized");
        return wd;
    }

    private void quitDriver(WebDriver wd) {
        if (wd == null) return;
        try {
            wd.quit();
            log.info("Selenium WebDriver closed");
        } catch (Exception e) {
            log.warn("Failed to quit WebDriver: {}", e.getMessage());
        }
    }

    public List<PropertyDto> scrapeUrl(String url, String sourceUrlTitle) {
        List<PropertyDto> listings = new ArrayList<>();
        log.info("Starting scrape for URL: {} (source: {})", url, sourceUrlTitle);

        String listingType = detectListingType(url);
        List<String> hrefs = new ArrayList<>();

        WebDriver driver = null;
        try {
            driver = createDriver();
            driver.get(url);
            Thread.sleep(3000);

            int previousCount = 0;
            int scrollAttempts = 0;

            while (scrollAttempts < divarConfig.getMaxScrollAttempts()) {
                List<WebElement> cards = driver.findElements(By.cssSelector("a[href*='/v/']"));
                int currentCount = cards.size();
                log.info("Found {} listing cards so far", currentCount);

                if (currentCount == previousCount && scrollAttempts > 0) {
                    log.info("No new cards found after scroll, stopping");
                    break;
                }
                previousCount = currentCount;

                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
                int randomScrollWait = ThreadLocalRandom.current().nextInt(4, 15);
                Thread.sleep(randomScrollWait * 1000L);
                scrollAttempts++;
            }

            List<WebElement> cardElements = driver.findElements(By.cssSelector("a[href*='/v/']"));
            for (WebElement card : cardElements) {
                try {
                    String href = card.getAttribute("href");
                    if (href != null && href.contains("/v/")) {
                        hrefs.add(href);
                    }
                } catch (Exception e) {
                    log.debug("Failed to read card href: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error scraping URL: {}", url, e);
        } finally {
            quitDriver(driver);
        }

        Set<String> processedTokens = new HashSet<>();
        for (String href : hrefs) {
            try {
                String token = extractTokenFromUrl(href);
                if (token == null) continue;

                if (propertyListingRepository.existsByToken(token)) continue;
                if (!processedTokens.add(token)) continue;

                PropertyDto dto = fetchAndBuildListing(token, listingType, href, sourceUrlTitle);
                if (dto != null) {
                    listings.add(dto);
                }

                int randomDelay = ThreadLocalRandom.current().nextInt(2, 6);
                Thread.sleep(randomDelay * 1000L);

            } catch (Exception e) {
                log.debug("Failed to process card: {}", e.getMessage());
            }
        }

        log.info("Extracted {} listings from URL: {}", listings.size(), url);
        return listings;
    }

    // ========== API-based listing fetch ==========

    private PropertyDto fetchAndBuildListing(String token, String listingType, String cardUrl, String sourceUrlTitle) {
        try {
            String apiUrl = DIVAR_API_BASE + token;
            log.debug("Fetching post from API: {}", apiUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.debug("API returned non-2xx for token {}: {}", token, response.getStatusCode());
                return null;
            }

            JsonObject apiResponse = JsonParser.parseString(response.getBody()).getAsJsonObject();

            Map<String, String> detailData = new HashMap<>();
            parseApiSections(apiResponse, detailData);

            String title = detailData.getOrDefault("title", "بدون عنوان");
            String fullDescription = detailData.get("fullDescription");

            log.info("Detail description for token {}: {}", token,
                fullDescription != null ? (fullDescription.length() > 100 ? fullDescription.substring(0, 100) + "..." : fullDescription) : "NULL");

            if (filterService.isBroker(fullDescription)) {
                log.info("Broker detected from description, skipping token: {}", token);
                return null;
            }

            String city = detailData.getOrDefault("city", "unknown");
            String district = detailData.getOrDefault("district", "");
            if (district.isBlank()) {
                district = city;
            }
            List<String> imageUrls = parseImageUrls(apiResponse);
            String link = "https://divar.ir/v/" + token;

            List<AttributeEntry> attributes = buildAttributesList(title, detailData, link);
            String attributesJson = PropertyDto.toJson(attributes);

            PropertyListing listing = PropertyListing.builder()
                    .token(token)
                    .title(title)
                    .description(fullDescription)
                    .url(link)
                    .listingType(listingType)
                    .imageUrls(new Gson().toJson(imageUrls))
                    .city(city)
                    .district(district)
                    .propertyType("apartment")
                    .isBroker(false)
                    .isSent(false)
                    .attributes(attributesJson)
                    .sourceUrlTitle(sourceUrlTitle)
                    .build();

            propertyListingRepository.save(listing);

            return PropertyDto.builder()
                    .token(token)
                    .title(title)
                    .listingType(listingType)
                    .imageUrls(imageUrls)
                    .url(link)
                    .isBroker(false)
                    .attributes(attributes)
                    .sourceUrlTitle(sourceUrlTitle)
                    .build();

        } catch (Exception e) {
            log.debug("Error fetching post from API for token {}: {}", token, e.getMessage());
            return null;
        }
    }

    private void parseApiSections(JsonObject apiResponse, Map<String, String> detailData) {
        if (!apiResponse.has("sections")) return;

        JsonArray sections = apiResponse.getAsJsonArray("sections");
        for (JsonElement sectionEl : sections) {
            try {
                JsonObject section = sectionEl.getAsJsonObject();
                String sectionName = section.has("section_name") ? section.get("section_name").getAsString() : "";

                if ("LIST_DATA".equals(sectionName)) {
                    parseListDataSection(section, detailData);
                } else if ("DESCRIPTION".equals(sectionName)) {
                    parseDescriptionSection(section, detailData);
                }
            } catch (Exception e) {
                // skip
            }
        }

        if (apiResponse.has("seo")) {
            parseSeoData(apiResponse.getAsJsonObject("seo"), detailData);
        }

        if (apiResponse.has("webengage")) {
            parseWebengageData(apiResponse.getAsJsonObject("webengage"), detailData);
        }
    }

    private void parseListDataSection(JsonObject section, Map<String, String> detailData) {
        if (!section.has("widgets")) return;

        JsonArray widgets = section.getAsJsonArray("widgets");
        for (JsonElement widgetEl : widgets) {
            try {
                JsonObject widget = widgetEl.getAsJsonObject();
                String widgetType = widget.has("widget_type") ? widget.get("widget_type").getAsString() : "";

                if ("GROUP_INFO_ROW".equals(widgetType)) {
                    parseGroupInfoRow(widget, detailData);
                } else if ("UNEXPANDABLE_ROW".equals(widgetType)) {
                    parseUnexpandableRow(widget, detailData);
                } else if ("GROUP_FEATURE_ROW".equals(widgetType)) {
                    parseGroupFeatureRow(widget, detailData);
                } else if ("SELECTOR_ROW".equals(widgetType)) {
                    parseSelectorRowModal(widget, detailData);
                } else if ("RENT_SLIDER".equals(widgetType)) {
                    parseRentSlider(widget, detailData);
                }
            } catch (Exception e) {
                // skip
            }
        }
    }

    private void parseGroupInfoRow(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");
            if (data.has("items")) {
                for (JsonElement item : data.getAsJsonArray("items")) {
                    JsonObject itemObj = item.getAsJsonObject();
                    String title = itemObj.has("title") ? itemObj.get("title").getAsString() : "";
                    String value = itemObj.has("value") ? itemObj.get("value").getAsString() : "";
                    if (!title.isEmpty() && !value.isEmpty()) {
                        detailData.put(title, value);
                        log.debug("GROUP_INFO_ROW: {} = {}", title, value);
                    }
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseUnexpandableRow(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");
            String title = data.has("title") ? data.get("title").getAsString() : "";
            String value = data.has("value") ? data.get("value").getAsString() : "";
            if (!title.isEmpty() && !value.isEmpty()) {
                detailData.put(title, value);
                log.debug("UNEXPANDABLE_ROW: {} = {}", title, value);
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseGroupFeatureRow(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");
            if (data.has("items")) {
                for (JsonElement item : data.getAsJsonArray("items")) {
                    JsonObject itemObj = item.getAsJsonObject();
                    String title = itemObj.has("title") ? itemObj.get("title").getAsString() : "";
                    boolean available = itemObj.has("available") && itemObj.get("available").getAsBoolean();
                    if (!title.isEmpty()) {
                        String normalizedTitle = title.replaceAll("\\s+ندارد$", "").trim();
                        detailData.put(normalizedTitle, available ? "دارد" : "ندارد");
                        log.debug("GROUP_FEATURE_ROW: {} = {}", normalizedTitle, available ? "دارد" : "ندارد");
                    }
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseRentSlider(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");

            if (data.has("credit")) {
                JsonObject credit = data.getAsJsonObject("credit");
                String value = credit.has("value") ? credit.get("value").getAsString() : null;
                if (value != null && !value.isEmpty()) {
                    detailData.putIfAbsent("ودیعه", value);
                    log.debug("RENT_SLIDER credit: {}", value);
                }
            }

            if (data.has("rent")) {
                JsonObject rent = data.getAsJsonObject("rent");
                String transformedValue = rent.has("transformed_value") ? rent.get("transformed_value").getAsString() : null;
                if (transformedValue != null && !transformedValue.isEmpty()) {
                    detailData.putIfAbsent("اجاره ماهانه", transformedValue);
                    log.debug("RENT_SLIDER rent: {}", transformedValue);
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseSelectorRowModal(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");
            String title = data.has("title") ? data.get("title").getAsString() : "";

            if (data.has("action")) {
                JsonObject action = data.getAsJsonObject("action");
                if (action.has("payload")) {
                    JsonObject payload = action.getAsJsonObject("payload");
                    if (payload.has("modal_page")) {
                        JsonObject modalPage = payload.getAsJsonObject("modal_page");
                        if (modalPage.has("widget_list")) {
                            parseModalWidgetList(modalPage.getAsJsonArray("widget_list"), detailData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseModalWidgetList(JsonArray widgetList, Map<String, String> detailData) {
        for (JsonElement widgetEl : widgetList) {
            try {
                JsonObject widget = widgetEl.getAsJsonObject();
                String widgetType = widget.has("widget_type") ? widget.get("widget_type").getAsString() : "";

                if ("UNEXPANDABLE_ROW".equals(widgetType)) {
                    parseUnexpandableRow(widget, detailData);
                } else if ("FEATURE_ROW".equals(widgetType)) {
                    parseFeatureRow(widget, detailData);
                }
            } catch (Exception e) {
                // skip
            }
        }
    }

    private void parseFeatureRow(JsonObject widget, Map<String, String> detailData) {
        try {
            JsonObject data = widget.getAsJsonObject("data");
            String title = data.has("title") ? data.get("title").getAsString() : "";
            if (!title.isEmpty()) {
                if (title.contains("ندارد")) {
                    String normalizedTitle = title.replaceAll("\\s+ندارد$", "").trim();
                    detailData.put(normalizedTitle, "ندارد");
                } else {
                    detailData.put(title, "دارد");
                }
                log.debug("FEATURE_ROW: {}", title);
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseDescriptionSection(JsonObject section, Map<String, String> detailData) {
        if (!section.has("widgets")) return;

        for (JsonElement widgetEl : section.getAsJsonArray("widgets")) {
            try {
                JsonObject widget = widgetEl.getAsJsonObject();
                String widgetType = widget.has("widget_type") ? widget.get("widget_type").getAsString() : "";

                if ("DESCRIPTION_ROW".equals(widgetType)) {
                    JsonObject data = widget.getAsJsonObject("data");
                    String text = data.has("text") ? data.get("text").getAsString() : "";
                    if (!text.isEmpty() && text.length() > 5) {
                        detailData.put("fullDescription", text);
                        log.debug("DESCRIPTION: {}", text.length() > 50 ? text.substring(0, 50) + "..." : text);
                        return;
                    }
                }
            } catch (Exception e) {
                // skip
            }
        }
    }

    private void parseSeoData(JsonObject seo, Map<String, String> detailData) {
        try {
            if (seo.has("web_info")) {
                JsonObject webInfo = seo.getAsJsonObject("web_info");
                if (webInfo.has("city_persian")) {
                    detailData.put("city", webInfo.get("city_persian").getAsString());
                }
                if (webInfo.has("district_persian")) {
                    detailData.put("district", webInfo.get("district_persian").getAsString());
                }
                if (webInfo.has("title")) {
                    detailData.putIfAbsent("title", webInfo.get("title").getAsString());
                }
            }

            if (seo.has("post_seo_schema")) {
                JsonObject schema = seo.getAsJsonObject("post_seo_schema");
                if (schema.has("name")) {
                    detailData.putIfAbsent("title", schema.get("name").getAsString());
                }
                if (schema.has("description")) {
                    detailData.putIfAbsent("fullDescription", schema.get("description").getAsString());
                }
                if (schema.has("numberOfRooms")) {
                    detailData.put("اتاق", schema.get("numberOfRooms").getAsString());
                }
                if (schema.has("floorSize") && schema.getAsJsonObject("floorSize").has("value")) {
                    detailData.put("متراژ", schema.getAsJsonObject("floorSize").get("value").getAsString());
                }
                if (schema.has("geo")) {
                    JsonObject geo = schema.getAsJsonObject("geo");
                    if (geo.has("latitude")) detailData.put("latitude", geo.get("latitude").getAsString());
                    if (geo.has("longitude")) detailData.put("longitude", geo.get("longitude").getAsString());
                    if (geo.has("address")) detailData.put("آدرس", geo.get("address").getAsString());
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private void parseWebengageData(JsonObject webengage, Map<String, String> detailData) {
        try {
            if (webengage.has("city")) detailData.putIfAbsent("city", webengage.get("city").getAsString());
            if (webengage.has("district")) detailData.putIfAbsent("district", webengage.get("district").getAsString());
            if (webengage.has("category")) detailData.put("category", webengage.get("category").getAsString());
            if (webengage.has("price") && webengage.get("price").getAsLong() > 0) {
                detailData.put("webengage_price", String.valueOf(webengage.get("price").getAsLong()));
            }
            if (webengage.has("rent") && webengage.get("rent").getAsLong() > 0) {
                detailData.put("webengage_rent", String.valueOf(webengage.get("rent").getAsLong()));
            }
            if (webengage.has("credit") && webengage.get("credit").getAsLong() > 0) {
                detailData.put("webengage_credit", String.valueOf(webengage.get("credit").getAsLong()));
            }
        } catch (Exception e) {
            // skip
        }
    }

    private List<String> parseImageUrls(JsonObject apiResponse) {
        List<String> urls = new ArrayList<>();
        try {
            if (apiResponse.has("sections")) {
                for (JsonElement sectionEl : apiResponse.getAsJsonArray("sections")) {
                    JsonObject section = sectionEl.getAsJsonObject();
                    if ("IMAGE".equals(section.get("section_name").getAsString())) {
                        for (JsonElement widgetEl : section.getAsJsonArray("widgets")) {
                            JsonObject widget = widgetEl.getAsJsonObject();
                            if ("IMAGE_CAROUSEL".equals(widget.get("widget_type").getAsString())) {
                                JsonObject data = widget.getAsJsonObject("data");
                                if (data.has("items")) {
                                    for (JsonElement item : data.getAsJsonArray("items")) {
                                        String url = item.getAsJsonObject().getAsJsonObject("image").get("url").getAsString();
                                        urls.add(url);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // skip
        }
        return urls;
    }

    // ========== Attributes builder ==========

    private List<AttributeEntry> buildAttributesList(String title, Map<String, String> detailData, String url) {
        List<AttributeEntry> attrs = new ArrayList<>();
        int order = 1;

        attrs.add(AttributeEntry.builder().key("title").title("عنوان").value(title).order(order++).build());

        addAttribute(attrs, "price", "قیمت کل", detailData, new String[]{"قیمت کل", "قیمت"}, order++);
        addAttribute(attrs, "deposit", "ودیعه", detailData, new String[]{"ودیعه", "رهن"}, order++);
        addAttribute(attrs, "monthlyRent", "اجاره ماهانه", detailData, new String[]{"اجاره ماهانه", "اجاره", "ماهانه"}, order++);
        addAttribute(attrs, "pricePerMeter", "قیمت هر متر", detailData, new String[]{"قیمت هر متر"}, order++);
        addAttribute(attrs, "area", "متراژ", detailData, new String[]{"متراژ", "مساحت"}, order++);
        addAttribute(attrs, "rooms", "اتاق", detailData, new String[]{"اتاق", "اتاق‌ها", "تعداد اتاق"}, order++);
        addAttribute(attrs, "floor", "طبقه", detailData, new String[]{"طبقه واحدها و تعداد واحد در طبقه", "طبقه"}, order++);
        addAttribute(attrs, "yearBuilt", "سال ساخت", detailData, new String[]{"ساخت", "سال ساخت"}, order++);
        addAttribute(attrs, "totalFloors", "تعداد کل طبقات", detailData, new String[]{"تعداد کل طبقات ساختمان", "تعداد طبقات"}, order++);
        addAttribute(attrs, "unitsPerFloor", "تعداد واحد در طبقه", detailData, new String[]{"تعداد واحد در طبقه"}, order++);
        addAttribute(attrs, "buildingDirection", "جهت ساختمان", detailData, new String[]{"جهت ساختمان", "جهت"}, order++);
        addAttribute(attrs, "deedType", "سند", detailData, new String[]{"سند"}, order++);
        addAttribute(attrs, "kitchenType", "نوع آشپزخانه", detailData, new String[]{"نوع آشپزخانه"}, order++);
        addAttribute(attrs, "buildingFacade", "نمای ساختمان", detailData, new String[]{"نمای ساختمان", "نما"}, order++);
        addAttribute(attrs, "occupancyStatus", "وضعیت سکونت", detailData, new String[]{"وضعیت سکونت"}, order++);
        addAttribute(attrs, "cabinetMaterial", "جنس کابینت", detailData, new String[]{"جنس کابینت"}, order++);
        addAttribute(attrs, "address", "آدرس", detailData, new String[]{"آدرس"}, order++);

        addFeatureAttribute(attrs, "elevator", "آسانسور", detailData, order++);
        addFeatureAttribute(attrs, "parking", "پارکینگ", detailData, order++);
        addFeatureAttribute(attrs, "storage", "انباری", detailData, order++);
        addFeatureAttribute(attrs, "balcony", "بالکن", detailData, order++);

        String fullDescription = detailData.get("fullDescription");
        if (fullDescription != null) {
            attrs.add(AttributeEntry.builder().key("fullDescription").title("توضیحات").value(fullDescription).order(order++).build());
        }

        attrs.add(AttributeEntry.builder().key("url").title("لینک").value(url).order(order++).build());

        return attrs;
    }

    private void addAttribute(List<AttributeEntry> attrs, String key, String title,
                              Map<String, String> data, String[] possibleKeys, int order) {
        for (String k : possibleKeys) {
            if (data.containsKey(k)) {
                attrs.add(AttributeEntry.builder().key(key).title(title).value(data.get(k)).order(order).build());
                return;
            }
        }
    }

    private void addFeatureAttribute(List<AttributeEntry> attrs, String key, String title,
                                     Map<String, String> data, int order) {
        if (data.containsKey(title)) {
            attrs.add(AttributeEntry.builder().key(key).title(title).value(data.get(title)).order(order).build());
        }
    }

    // ========== Utility methods ==========

    private String extractTokenFromUrl(String href) {
        String cleanUrl = href.split("\\?")[0];
        String[] pathParts = cleanUrl.split("/");
        if (pathParts.length >= 2) {
            String lastPart = pathParts[pathParts.length - 1];
            if (!lastPart.isEmpty() && lastPart.length() >= 3) {
                return lastPart;
            }
        }
        return null;
    }

    private String detectListingType(String url) {
        if (url.contains("/rent-")) return "اجاره";
        if (url.contains("/buy-")) return "فروش";
        if (url.contains("/sell")) return "فروش";
        if (url.contains("/rent")) return "اجاره";
        return "نامشخص";
    }
}
