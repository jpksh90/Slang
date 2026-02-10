if [ $# -eq 0 ]; then
  eval ./gradlew run
else
  eval ./gradlew run --args=\"$@\"
fi