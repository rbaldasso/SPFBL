#!/bin/sh

V='\033[01;31m'
D='\033[01;32m'
R='\033[0m'
echo -e "${D}Updater release: v0.1 ${R}"

BACKUP_DIR=/opt/spfbl/backup
AGORA=`date +%y-%m-%d-%H-%M`

if [ -f /etc/lsb-release ]; then
    . /etc/lsb-release
    else
    . /etc/init.d/functions
fi 

atualizaSistema(){

        echo -e "${V}\nIniciando procedimentos${R}"
        echo -e "${D}\n${R}"
        rm -Rf /tmp/spfbl-update/*
        mkdir -p /tmp/spfbl-update
        if [ ! -d "$BACKUP_DIR" ]; then
            mkdir -p "$BACKUP_DIR"
        fi
        #Baixa Arquivos
        wget "$URLDOWNLOAD" -O /tmp/spfbl-update/SPFBL.jar &> /dev/null
        var1=$(stat -c%s /tmp/spfbl-update/SPFBL.jar)
        var2=$(stat -c%s /opt/spfbl/SPFBL.jar)

    if [ "$var1" != "$var2" ]; then
        #Necessario atualizar
        echo -e "${D}Os arquivos são diferentes, o SPFBL será atualizado${R}"
        echo -e "${V}!!!!!!! Para cancelar, tecle CTRL+C AGORA! !!!!!!!!${R}"
        echo
        sleep 10
        echo -e "${D}Continuando atualização...${R}"
        fazBackup
        echo
        echo -e "${D}Verificando LIBs necessárias...${R}"
        cd /tmp/spfbl-update/
        wget https://github.com/leonamp/SPFBL/archive/master.zip &> /dev/null
        unzip master.zip &> /dev/null
        cd /tmp/spfbl-update/SPFBL-master/lib
        find . -name "*jar" -type f -exec ls -l {} \; | cut -d'/' -f 2 >> /tmp/spfbl-update/listadelibsGITHUB.txt
        cd /opt/spfbl/lib
        find . -name "*jar" -type f -exec ls -l {} \; | cut -d'/' -f 2 >> /tmp/spfbl-update/listadelibsLOCAL.txt
            if diff -q /tmp/spfbl-update/listadelibsGITHUB.txt /tmp/spfbl-update/listadelibsLOCAL.txt
                then
                echo "LIBs iguais, nao necessario altera-las"
            else
                echo "LIBs diferentes, necessario update"
                echo -e "${D}\nAtualizando Libs ${R}"
            fi
        rm -f /opt/spfbl/lib/*
        cp /tmp/spfbl-update/SPFBL-master/lib/* /opt/spfbl/lib/

        #Atualiza SPFBL.jar
        echo "Movendo o arquivo SPFBL.jar"
        echo
            if [ ! -f "/tmp/spfbl-update/SPFBL.jar" ]; then
                echo "Can't download https://github.com/SPFBL/beta-test/raw/master/SPFBL.jar"
                exit
            fi 

        echo "****   Current Version   ****"
        mostraVersao

        paraMta

        echo "**** SPFBL - Shutdown    ****"
        echo "SHUTDOWN" | nc 127.0.0.1 9875

        echo "**** SPFBL - Copy new v. ****"
        mv /opt/spfbl/SPFBL.jar $BACKUP_DIR/SPFBL.jar-"$AGORA"
        mv /tmp/spfbl-update/SPFBL.jar /opt/spfbl/SPFBL.jar
        echo "OK"

        echo "**** SPFBL - Starting    ****"
        /etc/init.d/spfbl start
        sleep 30

        if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "1" ]; then
            echo "OK - SERVICE ONLINE"
        else
            echo "FALHA - Verifique os logs e se necessario reverta do backup"
        fi

        iniciaMta
         
        echo "**** SPFBL - New Version ****"
        mostraVersao

        echo "****  F I N I S H E D !  ****"
        echo "Done."
        
    else
        
        echo "Os arquivos são iguais"

    fi

} 

iniciaMta(){

    if [ "$MTA" != "nenhum" ]; then
        echo "**** !  Starting MTA   ! ****"
        service "$MTA" start
        echo "OK"
    fi

}

paraMta(){

    if [ "$MTA" != "nenhum" ]; then
        echo "**** !!  Stoping MTA  !! ****"
        service "$MTA" stop
        echo "OK"
    fi

}

fazBackup(){

    echo -e "${V} **** SPFBL - Store cache ****${R}" 
    echo "STORE" | nc 127.0.0.1 9875

    echo -e "${V} **** SPFBL - Backup      ****${R}"
    echo "DUMP" | nc 127.0.01 9875 > "$BACKUP_DIR"/spfbl-dump-"$AGORA".txt
    tar -zcf "$BACKUP_DIR"/spfbl-backup-"$AGORA".tar /opt/spfbl &> /dev/null
    echo "BACKUP OK"

}

mostraVersao(){

    echo "VERSION" | nc 127.0.0.1 9875

}

############

if [ "$1" != "-u" ] && [ "$2" != "--m" ]; then
    echo -e "${V}\nScript de atualização do SPFBL${R}"
    echo -e "${D}\nParametros aceitos: ${R}"
    echo -e "${D}\n   -u   /  args. aceitos: stable e beta -> efetua a atualização${R}" 
    echo -e "${D}   Exemplo: -u stable  (opcao recomendada)${R}" 
    echo -e "${D}   Exemplo: -u beta${R}"
    echo -e "${D}\n   --------------------------------${R}"
    echo -e "${D}\n   -m     /  args. aceitos: nenhum , exim e postix${R}" 
    echo -e "${D}   Exemplo: -m nenhum${R}" 
    echo -e "${D}   Exemplo: -m exim${R}" 
    echo -e "${D}   Exemplo: -m postfix${R}" 

    exit

fi 

# Opcoes de controle (menu)
while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    -m|--mta)
    MTA="$2"
    shift # past argument
    ;;
    -u|--update)
    BRANCH="$2"
    shift # past argument
    ;;
esac
shift # past argument or value
done

if [ "$BRANCH" == "beta" ]; then
    URLDOWNLOAD="https://github.com/SPFBL/beta-test/raw/master/SPFBL.jar"
else 
    URLDOWNLOAD="https://github.com/leonamp/SPFBL/blob/master/dist/SPFBL.jar"
fi

atualizaSistema
