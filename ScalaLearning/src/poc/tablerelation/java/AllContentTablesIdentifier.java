package poc.tablerelation.java;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;

import poc.tablerelation.java.constants.CommonConstants;
import poc.tablerelation.java.constants.XMLConstants;

public class AllContentTablesIdentifier {

	private static final Properties programProperties = new Properties();
	
	public static void main(String[] args) throws Exception {
		AllContentTablesIdentifier tablesIdentifier = new AllContentTablesIdentifier();
		List<String> allContentTables = tablesIdentifier.readAllContentXML();
		System.out.println(allContentTables);
	}
	
	public List<String> readAllContentXML() throws IOException {
		
		List<String> allContentTables = new ArrayList<>();
		programProperties.load(ClassLoader.
				getSystemResourceAsStream(CommonConstants.PROGRAM_DETAILS_FILES));

		DirectoryStream<Path> directory = Files.newDirectoryStream(
				Paths.get(programProperties.getProperty(CommonConstants.ALL_CONTENT_FILE_PATH)));
		
		XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
		
		directory.forEach((path) -> { 

			XMLEventReader reader = null;
			try {
				
				System.out.println("Absolute Path : "+path.toString());
				reader = xmlInputFactory.createXMLEventReader(
						Files.newInputStream(path, StandardOpenOption.READ));

				boolean isTableNameTagStarted = false;
				boolean isTableNameCollected = false;
				while(reader.hasNext()) {
					XMLEvent event = reader.nextEvent();

					switch (event.getEventType()) {
					case XMLStreamConstants.START_ELEMENT:

						if(event.asStartElement().getName().getLocalPart().equals(XMLConstants.ELEMENT_TABLE_INFO)) {
							while(!isTableNameTagStarted) {
								XMLEvent childEvent = reader.nextEvent();
								if(childEvent.getEventType() == XMLStreamConstants.START_ELEMENT
										&& childEvent.asStartElement().getName().getLocalPart().equals(XMLConstants.ELEMENT_NAME)) {
									isTableNameTagStarted = true;
									isTableNameCollected = false;
									break;
								}
							}
						}
						break;

					case XMLStreamConstants.CHARACTERS:
						if(isTableNameTagStarted && !isTableNameCollected) {
							allContentTables.add(event.toString().trim());
							System.out.println(event.toString().trim());
							isTableNameCollected = true;
						}
						break;

					case XMLStreamConstants.END_ELEMENT:

						if(event.asEndElement().getName().getLocalPart().equals(XMLConstants.ELEMENT_TABLE_INFO)) {
							isTableNameTagStarted = false;
						}
						break;
					default:
						break;
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		
		return allContentTables;
	}
}
