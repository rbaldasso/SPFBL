#!/bin/sh

BACKUP_DIR=/opt/spfbl/backup
AGORA=`date +%y-%m-%d-%H-%M`
MTA=postfix

rm -r /tmp/spfbl-update
mkdir -p /tmp/spfbl-update
mkdir -p "$BACKUP_DIR"

wget https://github.com/leonamp/SPFBL/raw/master/dist/SPFBL.jar -O /tmp/spfbl-update/SPFBL.jar

var1=$(stat -c%s /tmp/spfbl-updateSPFBL.jar)

var2=$(stat -c%s /opt/spfbl/SPFBL.jar)

if [ "$var1" != "$var2" ]; then

echo "Os arquivos são diferentes"
echo "O SPFBL será atualizado"
echo "Para cancelar, tecle CTRL+C AGORA!"
echo
pause 10
echo "Continuando atualização..."
echo
echo "Verificando LIBs necessárias..."
echo

if [ ! -f "/opt/spfbl/lib/commons-codec-1.10.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/commons-codec-1.10.jar -O /opt/spfbl/lib/commons-codec-1.10.jar
fi

if [ ! -f "/opt/spfbl/lib/commons-lang3-3.3.2.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/commons-lang3-3.3.2.jar -O /opt/spfbl/lib/commons-lang3-3.3.2.jar
fi

if [ ! -f "/opt/spfbl/lib/commons-net-3.3.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/commons-net-3.3.jar -O /opt/spfbl/lib/commons-net-3.3.jar
fi

if [ ! -f "/opt/spfbl/lib/commons-validator-1.4.1.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/commons-validator-1.4.1.jar -O /opt/spfbl/lib/commons-validator-1.4.1.jar
fi

if [ ! -f "/opt/spfbl/lib/dnsjava-2.1.7.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/dnsjava-2.1.7.jar -O /opt/spfbl/lib/dnsjava-2.1.7.jar
fi

if [ ! -f "/opt/spfbl/lib/javax.mail.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/javax.mail.jar -O /opt/spfbl/lib/javax.mail.jar
fi

if [ ! -f "/opt/spfbl/lib/junique-1.0.4.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/junique-1.0.4.jar -O /opt/spfbl/lib/junique-1.0.4.jar
fi

if [ ! -f "/opt/spfbl/lib/recaptcha4j-0.0.7.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/recaptcha4j-0.0.7.jar -O /opt/spfbl/lib/recaptcha4j-0.0.7.jar
fi

if [ ! -f "/opt/spfbl/lib/zxing-2.1.jar" ]; then
wget https://github.com/leonamp/SPFBL/raw/master/lib/zxing-2.1.jar -O /opt/spfbl/lib/zxing-2.1.jar
fi

echo "Fazendo download do SPFBL.jar"
echo

if [ ! -f "/tmp/spfbl-update/SPFBL.jar" ]; then
    echo "Can't download https://github.com/SPFBL/beta-test/raw/master/SPFBL.jar"
fi


echo "****    SPFBL  UPDATE    ****"
echo

echo "****   Current Version   ****"
echo "VERSION" | nc 127.0.0.1 9875

echo "**** !!  Stoping MTA  !! ****"
service "$MTA" stop
echo "OK"

echo "**** SPFBL - Store cache ****"
echo "STORE" | nc 127.0.0.1 9875

echo "**** SPFBL - Backup      ****"
echo "DUMP" | nc 127.0.0.1 9875 > "$BACKUP_DIR"/spfbl-dump-"$AGORA".txt
echo "OK"

echo "**** SPFBL - Shutdown    ****"
echo "SHUTDOWN" | nc 127.0.0.1 9875

echo "**** SPFBL - Copy new v. ****"
mv /opt/spfbl/SPFBL.jar $BACKUP_DIR/SPFBL.jar-"$AGORA"
mv /tmp/spfbl-update/SPFBL.jar /opt/spfbl/SPFBL.jar
echo "OK"

echo "**** SPFBL - Starting    ****"
cd /opt/spfbl/
java -jar SPFBL.jar &
cd /root/
sleep 30

if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "1" ]; then
    echo "OK"
else
    exit -1
fi

rm -r /tmp/spfbl-update

echo "**** !  Starting MTA   ! ****"
service "$MTA" start
echo "OK"
 
echo "**** SPFBL - New Version ****"
echo "VERSION" | nc 127.0.0.1 9875

echo "****  F I N I S H E D !  ****"
echo "Done."

else
    echo "Os arquivos são iguais"

rm -r /tmp/spfbl-update

fi
