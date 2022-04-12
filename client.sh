# stty and Java 1.8+ must be available on $PATH
HBNDFMPMHPGNIIOM=$(stty -g)
stty -echo -icanon
java -jar ./client.jar
stty $HBNDFMPMHPGNIIOM
unset HBNDFMPMHPGNIIOM
