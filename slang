if [ $# -eq 0 ]; then
  eval ./gradlew run --console=plan --quiet
else
  eval ./gradlew run --console=plain --quiet --args=\"$@\"
fi
