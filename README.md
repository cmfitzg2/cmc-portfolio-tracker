# cmc-portfolio-tracker

### A portfolio tracker for coins listed on CoinMarketCap

As long as holdings.txt (tab-delimitted file) contains your actual current crypto holdings, running CryptoTracker will update it with the current value of the portfolio.

Column 1 is the "slug" (found in the URL of the coins detail page). Can add or remove from this as needed.
Column 2 is the quantity held of the given asset. Can update as needed.
Column 3 is output by running the JAR, as well as the Grand Total field. The sample holdings file in this repo doesn't have this yet.

You should be able to easily view holdings.txt in OpenOffice calc or Excel if you open it as a tab-delimitted file (make sure not to have comma delimitting or the price formatting will break it (there's commas))

Supply your CoinMarketCap API key in `apiKey`.

Depends on apache.httpcomponents.httpclient and fasterxml.jackson.core.databind