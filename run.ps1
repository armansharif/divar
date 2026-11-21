$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "D:\chromedriver-win64\chromedriver-win64;" + $env:Path
cd F:\Projects\NewDivarExtraction
mvn spring-boot:run
