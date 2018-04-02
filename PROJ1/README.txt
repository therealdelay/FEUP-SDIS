Para compilar:

javac *.java


Para correr um peer:

	java Server <version> <id> <MC> <MDB> <MDR>

Em que:
	version: vers�o do protocolo no formato <n>.<m>, em que n e m s�o digitos.
	id: um n�mero, identificador do peer e do acesso por rmi.
	MC, MDB, MDR: identificadores dos canais de multicast no formato ip:port

Exemplo:
	java Server 1.0 1 228.5.6.7:6789 228.5.6.8:6790 228.5.6.9:6791



Para correr a testApp:

	java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>

Em que:
	peer_ap: ponto de acesso ao initiator peer por rmi no formato ip/id, em que o ip e id correspondem aos do peer, que ter� o papel de initiator.
	opnd_1: no caso de ser um nome de ficheiro (BACKUP e RESTORE), poder� ser o seu path relativo ou absoluto.

Exemplo:
	java TestApp localhost/1 backup bar.gif 2