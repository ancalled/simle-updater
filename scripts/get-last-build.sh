#!/bin/sh

TEAMCITY_URL=""       # URL of teamcity server without http
LOGIN=""              # Teamcity user
PASS=""               # Teamcity password

BUILD_TYPE_ID=""      # Id of application in teamcity
ARTIFACT=""           # Build arhive name from temacity

WEB_HOME=""           # Path to web shared folder

PAGE_TEMPL="download-page.xsl" #Template for apllication's download page



SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

BUILD=`curl -s http://"$LOGIN":"$PASS"@"$TEAMCITY_URL"/httpAuth/app/rest/buildTypes/id:"$BUILD_TYPE_ID"/builds?status=SUCCESS | xsltproc last-build.xsl -`

echo "Last build number: $BUILD"

OUTDIR="$WEB_HOME/$BUILD"

if [ -d "$OUTDIR" ]
then
  echo "Build directory $BUILD is already exists!"
  exit 1
fi


cd "$3"

if [ -f "$ARTIFACT" ]
then
 rm "$ARTIFACT"
fi

wget -q http://"$LOGIN":"$PASS"@"$TEAMCITY_URL"/httpAuth/repository/download/"$BUILD_TYPE_ID"/.lastSuccessful/"$ARTIFACT"

echo "$BUILD" > last-build


mkdir "./$BUILD"
cp "$ARTIFACT" "./$BUILD"

cd "./$BUILD"

unzip -q "$ARTIFACT"
rm "$ARTIFACT"

cd ../
#echo "Script dir: $SCRIPT_DIR"
#echo "Current dir: `pwd`"

xsltproc --param last_build $BUILD --param artifact $ARTIFACT  $SCRIPT_DIR/$PAGE_TEMPL ./$BUILD/build-changes.xml > index.html


PREVBUILD="$BUILD"
FOUND='no'

while [ "$PREVBUILD" -gt 0 ]; do
    let PREVBUILD-=1
    if [ -d "$PREVBUILD" ]; then
        FOUND='yes'
        break
    fi    
done

                                                                
if [ "$FOUND" == 'yes' ]; then
    echo "Previous build: $PREVBUILD"
    
    DIFF=`cd "$BUILD" && diff -qr ./ ../$PREVBUILD | awk '
               /^Files/ { sub(/:$/, "", $2); print $2 }
               /^Only/  { sub(/:/, "/", $3); print $3 $4 }
          ' && cd ../`
#    DIFF2 = `cd "$BUILD" && diff -qr ./ ../$PREVBUILD && cd ../`
    echo 'Diff:'
    echo "$DIFF2"
    
    echo "$DIFF" > "./$BUILD/changes"
fi
                                                                    
