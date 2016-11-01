#!/bin/sh

V='\033[01;31m'
D='\033[01;32m'
R='\033[0m'
echo -e "${D}Updater release: v0.1 ${R}"

BACKUP_DIR=/opt/spfbl/backup
AGORA=`date +%y-%m-%d-%H-%M`
--> alterar MTA=postfix 

atualizaSistema(){

        rm -f /tmp/spfbl-update/*
        rmdir /tmp/spfbl-update/
        mkdir -p /tmp/spfbl-update
        if [ ! -d "$BACKUP_DIR" ]; then
            mkdir -p "$BACKUP_DIR"
        fi
        #Baixa Arquivos
        wget https://github.com/leonamp/SPFBL/raw/master/dist/SPFBL.jar -O /tmp/spfbl-update/SPFBL.jar
        var1=$(stat -c%s /tmp/spfbl-updateSPFBL.jar)
        var2=$(stat -c%s /opt/spfbl/SPFBL.jar)

        if [ "$var1" != "$var2" ]; then
        #Necessario atualizar
        echo "Os arquivos são diferentes"
        echo "O SPFBL será atualizado"
        echo "Para cancelar, tecle CTRL+C AGORA!"
        echo
        pause 10
        echo "Continuando atualização..."
        echo
        echo "Verificando LIBs necessárias..."
        # baixar pasta de libs, pegar lista de nomes, comparar com a pasta atual 
        #
        #
        #
        #Atualiza SPFBL.jar
        echo "Movendo o arquivo SPFBL.jar"
        echo
            if [ ! -f "/tmp/spfbl-update/SPFBL.jar" ]; then
                echo "Can't download https://github.com/SPFBL/beta-test/raw/master/SPFBL.jar"
                exit
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

        fi

} 


else
    echo "Os arquivos são iguais"

rm -r /tmp/spfbl-update

fi


if [ "$1" != "--update" ] && [ "$2" != "--exim" ] OR [ "$2" != "--postfix" ]; then
    echo -e "${V}\nScript de atualização do SPFBL${R}"
    echo -e "${D}\nParametros aceitos: ${R}"
    echo -e "${D}   --update   /  efetua a atualização${R}" 
    echo -e "${D}   --exim     /  para o exim durante atualizacao${R}" 
    echo -e "${D}   --postfix  /  para o postfix durante atualizacao${R}" 
fi 
