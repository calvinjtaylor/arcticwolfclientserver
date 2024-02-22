
# Arctic Wolf Java Client/Server Assignment 

## Overview
Hello and welcome to my client server demo.  I hope it meets expectations.  I was shocked at how many places I could have improved the project given more time.
I used JSON to transport the map to the server, as it is easily expandable and there are a metric ton of plugins for dealing with JSON.  Simple parameter passing would have been sufficient, but I have developed a dislike for maintaining them, especially if the method/class structure is larger than a couple. 
I used a structured logging framework as it's easier for a log analysis tool to ingest structured logs. I ended up running with the default layout, which seems to have excessive detail, but that could be cleaned up. 
I used wiremock for unit testing, and have written a functional test to show that the classes work.
## Author 
Calvin Taylor 

## Running
./gradlew build --info
This will assemble the jars, and run tests on the server classes, showing that they do in fact work.

Now I was asked to show how I would run it, and I believe that is shown above, however the classes can be run via command line with a command such as
java -cp "./build/libs/articwolfscanner-1.0-SNAPSHOT.jar:dependentjar1path" org.caltaylor.server.JsonServer <serverconfigfile>
I know gradle puts them all in the cache and they can be found, but doing so is time consuming and proves little about my programming skills.  

## References
https://www.baeldung.com/java-structured-logging

## Notes
I enjoyed the process of completing this assignment.  I did use openai for reference and some boiler plate, but it's information is dated and is barely usable.  I had some issues with threading and timing of tests causing some failures which have all been addressed.
