cd java-project
mvn clean compile
mvn exec:java
cd ..
cp -R java-project/output .
# mv output ~/Library/Mobile\ Documents/com~apple~CloudDocs/results