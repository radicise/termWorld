# stty and Java 1.8+ must be available on $PATH
# This script is currently only configured for demo mode;
# `client.jar' must be launched with different arguments in order to use a different login
HBNDFMPMHPGNIIOM=$(stty -g)
stty -echo -icanon
java -jar ./client.jar guest password 5
stty $HBNDFMPMHPGNIIOM
unset HBNDFMPMHPGNIIOM
