import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

public class Main {

    private static final String apiKey = "PUT-YOUR-API-KEY-HERE";
    private static final String uri = "https://pro-api.coinmarketcap.com/v2/cryptocurrency/quotes/latest";
    private static ObjectMapper objectMapper;
    private static NumberFormat currencyFormatter;
    private static double usdOffset = 0;

    public static void main(String[] args) {
        objectMapper = new ObjectMapper();
        currencyFormatter = NumberFormat.getCurrencyInstance();
        Map<String, Double> holdings = readHoldings();
        if (holdings == null) {
            System.out.println("holdings was null");
            return;
        }
        Map<String, Double> prices = fetchPrices(holdings);
        calculatePortfolioValue(prices, holdings);
    }

    private static Map<String, Double> readHoldings() {
        Map<String, Double> holdings = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("holdings.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    String slug = parts[0];
                    if (slug.equals("Grand total:")) {
                        break;
                    }
                    double quantity = Double.parseDouble(parts[1]);
                    if (slug.equals("unsupported-tokens-usd-value")) {
                        usdOffset = quantity;
                    } else {
                        holdings.put(slug, quantity);
                    }
                }
            }
            return holdings;
        } catch (IOException e) {
            System.out.println("Error reading holdings file: " + e.getMessage());
            return null;
        } catch (NumberFormatException e) {
            System.out.println("Error parsing quantity: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Double> fetchPrices(Map<String, Double> holdings) {
        List<NameValuePair> parameters = new ArrayList<>();
        int index = 0;
        StringBuilder slugCsv = new StringBuilder();
        for (Map.Entry<String, Double> entry : holdings.entrySet()) {
            if (index != 0) {
                slugCsv.append(",");
            }
            slugCsv.append(entry.getKey());
            index++;
        }
        parameters.add(new BasicNameValuePair("slug", slugCsv.toString()));
        String result = makeAPICall(parameters);
        return parseApiResponse(result);
    }

    private static Map<String, Double> parseApiResponse(String jsonResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            JsonNode dataNode = jsonNode.get("data");

            Map<String, Double> cryptoPrices = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> dataIterator = dataNode.fields();
            while (dataIterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = dataIterator.next();
                JsonNode quoteNode = entry.getValue().get("quote").get("USD");
                double price = quoteNode.get("price").asDouble();
                String slug = entry.getValue().get("slug").textValue();
                cryptoPrices.put(slug, price);
            }
            return cryptoPrices;
        } catch (IOException e) {
            System.out.println("Error parsing API response: " + e.getMessage());
            return null;
        }
    }

    private static void calculatePortfolioValue(Map<String, Double> prices, Map<String, Double> holdings) {
        double totalValue = 0.0;
        List<String[]> rows = new ArrayList<>();

        // Create a list to store entries from the holdings map
        List<Map.Entry<String, Double>> sortedHoldings = new ArrayList<>(holdings.entrySet());

        // Sort the entries based on total USD value in descending order
        sortedHoldings.sort((a, b) -> {
            double valueA = a.getValue() * prices.getOrDefault(a.getKey(), 0.0);
            double valueB = b.getValue() * prices.getOrDefault(b.getKey(), 0.0);
            return Double.compare(valueB, valueA);
        });

        for (Map.Entry<String, Double> entry : sortedHoldings) {
            String slug = entry.getKey();
            double quantity = entry.getValue();
            if (prices.containsKey(slug)) {
                double price = prices.get(slug);
                double value = price * quantity;
                totalValue += value;
                rows.add(new String[]{slug, Double.toString(quantity), currencyFormatter.format(value)});
            } else {
                System.out.println("Coin with slug " + slug + " not found in API response.");
            }
        }

        if (usdOffset != 0) {
            rows.add(new String[]{"unsupported-tokens-usd-value", Double.toString(usdOffset), currencyFormatter.format(usdOffset)});
            totalValue += usdOffset;
        }

        rows.add(new String[]{"Grand total:", "", currencyFormatter.format(totalValue)});
        writeTxt("holdings.txt", rows);
    }

    private static void writeTxt(String filename, List<String[]> rows) {
        try (FileWriter writer = new FileWriter(filename)) {
            for (String[] row : rows) {
                writer.append(String.join("\t", row)).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String makeAPICall(List<NameValuePair> parameters) {
        String response_content = "";
        try {
            URIBuilder query = new URIBuilder(uri);
            query.addParameters(parameters);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet request = new HttpGet(query.build());

            request.setHeader(HttpHeaders.ACCEPT, "application/json");
            request.addHeader("X-CMC_PRO_API_KEY", apiKey);

            try (CloseableHttpResponse response = client.execute(request)) {
                System.out.println(response.getStatusLine());
                HttpEntity entity = response.getEntity();
                response_content = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
            }
        } catch (Exception e) {
            System.out.println("the API call didn't work. " + e.getMessage());
        }

        return response_content;
    }

    public static String prettyPrint(ObjectMapper objectMapper, String uglyString) {
        try {
            Object jsonObject = objectMapper.readValue(uglyString, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            System.out.println(e);
        }
        return "Error pretty printing";
    }

}