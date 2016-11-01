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
        wget "$URLDOWNLOAD" -O /tmp/spfbl-update/SPFBL.jar
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
        mostraVersao

        paraMta

        fazBackup

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

        fi

} 

paraMta(){

    echo "**** !!  Stoping MTA  !! ****"
    service "$MTA" stop
    echo "OK"

}

fazBackup(){

    echo "**** SPFBL - Store cache ****"
    echo "STORE" | nc 127.0.0.1 9875

    echo "**** SPFBL - Backup      ****"
    echo "DUMP" | nc 127.0.01 9875 > "$BACKUP_DIR"/spfbl-dump-"$AGORA".txt
    tar -zcf "$BACKUP_DIR"/spfbl-backup-"$AGORA".tar /opt/spfbl
    echo "BACKUP OK"

}

iniciaMta(){

    echo "**** !  Starting MTA   ! ****"
    service "$MTA" start
    echo "OK"

}

mostraVersao(){

    echo "VERSION" | nc 127.0.0.1 9875

}

else
    echo "Os arquivos são iguais"

rm -r /tmp/spfbl-update

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
    $URLDOWNLOAD="https://github.com/SPFBL/beta-test/raw/master/SPFBL.jar"
else 
    $URLDOWNLOAD="https://github.com/leonamp/SPFBL/blob/master/dist/SPFBL.jar"
fi
############

if [ "$1" != "-u" ] && [ "$2" != "--m" ]; then
    echo -e "${V}\nScript de atualização do SPFBL${R}"
    echo -e "--"    
    echo -e "${D}\nParametros aceitos: ${R}"
    echo -e "${D}   -u   /  args. aceitos: stable e beta efetua a atualização${R}" 
    echo -e "${D}   Exemplo: -u stable  (opcao default)${R}" 
    echo -e "${D}   Exemplo: -u beta${R}"
    echo -e "--"
    echo -e "${D}   --m     /  args. aceitos: nenhum , exim e postix${R}" 
    echo -e "${D}   Exemplo: -m nenhum  (opcao default)${R}" 
    echo -e "${D}   Exemplo: -m exim${R}" 
    echo -e "${D}   Exemplo: -m postfix${R}" 
fi 
