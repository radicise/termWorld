# stty and Java 1.8+ must be available
# This script is currently only configured for demo mode;
# `client.jar' must be launched with different arguments in order to use a different login or different authentication or multiplayer servers
HBNDFMPMHPGNIIOM=$(stty -g)
stty -echo -icanon
java TWRoot/TWClient/Client guest password 0000000000000005 127.0.0.1:15651 127.0.0.1:15652
stty $HBNDFMPMHPGNIIOM
unset HBNDFMPMHPGNIIOM
