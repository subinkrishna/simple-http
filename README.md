# simple-http
A WIP simple basic HTTP library 

# Usage
```java
String res1 = Http.get("http://www.bing.com/search")
                  .query(Pair.of("q", "apple"))
                  .userAgent("MyFancyUserAgent") // Custom User-Agent
                  .verbose() // Prints log
                  .noRedirect() // No auto-redirections on 3xx
                  .asString();
```
