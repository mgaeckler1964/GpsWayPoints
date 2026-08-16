# GpsWayPoints
 
Dieses Programm zeigt die Richtung und die Entfernng zu einem bekannten 
Punkt auf der Erde.

Die Funktionen im Menü:
1. Standort speichern: Merkt sich Ihre aktuelle Position und zeigt von nun an die Richtung und Entfernung zum Ziel.
2. Standort mit Namen Speichern: Speichert den aktuellen Standort mit Namen, so dass Sie diesen jederzeit wieder herstellen können.
3. Weg aufzeichnen. Speichert einen GPX-Track Ihres aktuellen Weges. 
4. Ziel mit Namen Speichern. Speichert den zuletzt geladenen Wegpunkt unter einem neuen Namen.
5. Wegpunkt zeigen. Zeigt Ihnen einen Wegpunkt, den Sie auswählen können, auf der Karte.
6. Wegpunkt laden. Lädt einen Wegpunkt, den Sie auswählen können, und verwendet diesen als neues Ziel. Entfernung und Richtung wird Ihnen im Kompass angezeigt.
7. Wegpunkt löschen. Löscht einen Wegpunkt.
8. GPS-Intervall. Hiermit legen Sie fest, wie oft das GPS-Signal gelesen werden soll. Beachten Sie aber, der eingebaute GPS-Empfänger ist oft nicht in der Lage, häufiger als einmal pro Sekunde eine Position zu liefern.
9. Einrichtung. Zeigt das Einrichtungsmenü. Siehe unten.
10. Über GpsWayPoints. Zeigt Versionshinweise.
11. Beenden. Beendet den GPS-Empfang und die Activity.

Das Einrichtungsmenü:
1. Dokumentordner. Speichert und liest alle Dateien aus einem Unterordner im Dokumentenverzeichniss des Telefons. Für Android 10 und älter, werden Sie um Schreib- und Leserechte gebeten. Für Android 11 und neuer müssen Sie die Speicherverwaltung erlauben.
2. GPX-Ordner. Dies erlaubt es Ihnen, einen Ordner für die Dateioperationen auszuwählen. Wenn Sie das gemacht haben, braucht die Anwendung weder das Recht zur Speicherverwaltung noch das Recht externen Speicher zu schreiben und zu lesen. Beachten Sie aber, dass einige Telefone hier keine Verzeichnisse vom internen Speicher erlauben. Was auch immer die Hersteller sich dabei gedacht haben.
3. GPX-Wegpunkte speichern. Speichert die Wegpunkte als Text- und GPX-Datei.
4. Wegpunktdatei laden. Lädt die Textdatei mit den Wegpunkten.
5. Kallibrierung. Liest mehrere Positionsangaben und ermittelt den Durchschnitt. Wenn die Kallibrierung eingaschaltet ist, warten Sie, bis die Positionsanzeige stabil bleibt und keine Abweichungen mehr meldet, dann speichern Sie die aktuelle Position bevor die Kallibrierung wieder abgeschaltet wird.
6. Darkmode. Ändert das Erscheinungsbild von dunkel zu hell und umgekehrt.
7. Kartenansichet. Wechselt von der Kompassansicht zur Kartenansicht mit Openstreetmap und umgekehrt.
8. Folge Position. Scrollt die Kartenansicht immer zum aktuellen Standort.
9. Speichervewaltung. Nur bei Android 11 und neuer. Hiermit können Sie das Recht der Speicherverwaltung gewähren oder entziehen.
10. Zusätliche Positionierung. Zeigt auf der Karte zusätzlich zum GPS-Standort noch andere Provider wie Netzwork und Fused an. Das ist nützlich, um zu sehen, wie stark die anderen Locationprovider vom GPS-Signal abweichen.
11. Benachrichtigung. Hiermit können Sie die Benachrichtigung (de)aktivieren. Benachrichtigunen sind nützlich, um zu sehen, ob der GPS-Service der Anwendung gerade läuft oder nicht. Wenn Sie einen GPX-Track gerade aufzeichen, wird die Aufzeichnung gestoppt, wenn Sie auf die Benachrichtigung klicken.

Zum Übersetzen der Quellen habe ich 

Android Studio Narwhal | 2025.1.1
Build #AI-251.25410.109.2511.13665796

verwendet. Das gebaute Package kann hier

https://www.gaeckler.at/Software/software.htm#GpsWayPoints (in Wien)

oder hier

http://www.gäckler.de/Software/software.htm#GpsWayPoints (in Deutschland)

geladen werden.

---

To build the package I used

Android Studio Narwhal | 2025.1.1
Build #AI-251.25410.109.2511.13665796

The compiled package can be downloaded from

https://www.gaeckler.at/Software/software.htm#GpsWayPoints (in Vienna)

or

http://www.gäckler.de/Software/software.htm#GpsWayPoints (in Germany)

## Screenshot
<img width="270" height="585" alt="GpsWayPoints with Compass" src="https://github.com/user-attachments/assets/e6b8bc38-5d87-4702-a703-1328bdd11ab4" />

<img width="270" height="585" alt="GpsWayPoints with Map" src="https://github.com/user-attachments/assets/273da7ec-9c6f-4b1f-8204-bd30a493df2c" />


WayPoints with Dark mode

