package PracticaAdatUd6;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XPathQueryService;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            String driver = "org.exist.xmldb.DatabaseImpl";
            String URI = "xmldb:exist://localhost:8080/exist/xmlrpc/db/gimnasio";
            String user = "admin";
            String pass = "";

            initDatabase(driver);

            Collection col = DatabaseManager.getCollection(URI, user, pass);
            if (col == null) {
                System.out.println("No existe la colección.");
                return;
            }

            generarXMLIntermedio(col);
            subirXML(col, "src/main/resources/xml/archivoOriginal.xml");
            generarXMLFinal(col);
            subirXML(col, "src/main/resources/xml/archivoFinal.xml");

            col.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initDatabase(String driver) {
        try {
            Class<?> cl = Class.forName(driver);
            Database database = (Database) cl.getDeclaredConstructor().newInstance();
            DatabaseManager.registerDatabase(database);
        } catch (Exception e) {
            System.out.println("Error al inicializar la BD eXist");
            e.printStackTrace();
        }
    }

    private static void generarXMLIntermedio(Collection col) throws XMLDBException {
        XPathQueryService servicio = (XPathQueryService) col.getService("XPathQueryService", "1.0");
        Document doc = crearDocumento();
        Element infoGimnasio = doc.createElement("infoGimnasio");
        doc.appendChild(infoGimnasio);

        String query = "for $uso in /USO_GIMNASIO/fila_uso return $uso";
        ResourceSet result = servicio.query(query);
        ResourceIterator i = result.getIterator();

        if (!i.hasMoreResources()) {
            System.out.println("La consulta está vacía");
            return;
        }

        while (i.hasMoreResources()) {
            Element datos = doc.createElement("datos");
            infoGimnasio.appendChild(datos);
            String contenido = i.nextResource().getContent().toString();

            String codigoSocio = extraerValor(contenido, "<CODSOCIO>", "</CODSOCIO>");
            String nombreSocio = obtenerNombreSocio(servicio, codigoSocio);
            String codigoActividad = extraerValor(contenido, "<CODACTIV>", "</CODACTIV>");
            String nombreActividad = obtenerNombreActividad(servicio, codigoActividad);
            String horaInicio = extraerValor(contenido, "<HORAINICIO>", "</HORAINICIO>");
            String horaFin = extraerValor(contenido, "<HORAFINAL>", "</HORAFINAL>");
            int horas = Integer.parseInt(horaFin) - Integer.parseInt(horaInicio);
            String tipoActividad = obtenerTipoActividad(servicio, codigoActividad);

            aniadirElemento(doc, datos, "COD", codigoSocio);
            aniadirElemento(doc, datos, "NOMBRESOCIO", nombreSocio);
            aniadirElemento(doc, datos, "CODACTIV", codigoActividad);
            aniadirElemento(doc, datos, "NOMBREACTIVIDAD", nombreActividad);
            aniadirElemento(doc, datos, "horas", String.valueOf(horas));

            int cantidad = obtenerCantidad(tipoActividad);
            aniadirElemento(doc, datos, "tipoact", tipoActividad);
            aniadirElemento(doc, datos, "cuota_adicional", (cantidad * horas) + "€");
        }

        guardarDocumento(doc, "src/PracticaAdatUd6/resources/xml/archivoOriginal.xml");
    }

    private static String extraerValor(String content, String startTag, String endTag) {
        return content.split(startTag)[1].split(endTag)[0];
    }

    private static String obtenerNombreSocio(XPathQueryService servicio, String codigoSocio) throws XMLDBException {
        String querySocio = "/SOCIOS_GIM/fila_socios[COD='" + codigoSocio + "']/NOMBRE/text()";
        ResourceSet resultSocio = servicio.query(querySocio);
        ResourceIterator iSocio = resultSocio.getIterator();
        if (iSocio.hasMoreResources()) {
            return iSocio.nextResource().getContent().toString().trim();
        }
        return "";
    }

    private static String obtenerNombreActividad(XPathQueryService servicio, String codigoActividad) throws XMLDBException {
        String queryActividad = "/ACTIVIDADES_GIM/fila_actividades[@cod='" + codigoActividad + "']/NOMBRE/text()";
        ResourceSet resultActividad = servicio.query(queryActividad);
        ResourceIterator iActividad = resultActividad.getIterator();
        if (iActividad.hasMoreResources()) {
            return iActividad.nextResource().getContent().toString().trim();
        }
        return "";
    }

    private static String obtenerTipoActividad(XPathQueryService servicio, String codigoActividad) throws XMLDBException {
        String queryActividadTipo = "/ACTIVIDADES_GIM/fila_actividades[@cod='" + codigoActividad + "']/@tipo";
        ResourceSet resultActividadTipo = servicio.query(queryActividadTipo);
        ResourceIterator iActividadTipo = resultActividadTipo.getIterator();
        if (iActividadTipo.hasMoreResources()) {
            return iActividadTipo.nextResource().getContent().toString().trim();
        }
        return "";
    }

    private static int obtenerCantidad(String tipoActividad) {
        switch (Integer.parseInt(tipoActividad)) {
            case 1:
                return 0; // libre horario
            case 2:
                return 2; // grupo
            default:
                return 4; // alquila un espacio
        }
    }

    private static Document crearDocumento() {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            return docBuilder.newDocument();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void guardarDocumento(Document doc, String filePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult streamResult = new StreamResult(new File(filePath));
            transformer.transform(source, streamResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void subirXML(Collection col, String ruta) {
        File f = new File(ruta);
        if (!f.canRead()) {
            System.err.println("Error al leer el archivo");
        } else {
            try {
                Resource nuevoRecurso = col.createResource(f.getName(), "XMLResource");
                nuevoRecurso.setContent(f);
                col.storeResource(nuevoRecurso);
            } catch (XMLDBException e) {
                e.printStackTrace();
            }
        }
    }

    private static void generarXMLFinal(Collection col) {
        try {
            XPathQueryService servicio = (XPathQueryService) col.getService("XPathQueryService", "1.0");
            Document doc = crearDocumento();
            Element infoGimnasio = doc.createElement("infoGimnasio");
            doc.appendChild(infoGimnasio);

            String query = "for $persona in /SOCIOS_GIM/fila_socios return $persona";
            ResourceSet result = servicio.query(query);
            ResourceIterator i = result.getIterator();

            if (!i.hasMoreResources()) {
                System.out.println("La consulta está vacía");
                return;
            }

            while (i.hasMoreResources()) {
                String contenido = i.nextResource().getContent().toString();
                String codigoSocio = extraerValor(contenido, "<COD>", "</COD>");
                String nombreSocio = extraerValor(contenido, "<NOMBRE>", "</NOMBRE>");
                String cuotaFija = extraerValor(contenido, "<CUOTA_FIJA>", "</CUOTA_FIJA>");

                String queryCuotaAdicional = "sum(/USO_GIMNASIO/fila_uso[CODSOCIO='" + codigoSocio + "']/cuota_adicional)";
                ResourceSet resultCuota = servicio.query(queryCuotaAdicional);
                String sumaCuotaAdicional = "0";
                ResourceIterator iCuota = resultCuota.getIterator();
                if (iCuota.hasMoreResources()) {
                    sumaCuotaAdicional = iCuota.nextResource().getContent().toString().trim();
                }

                Element socio = doc.createElement("socio");
                infoGimnasio.appendChild(socio);
                aniadirElemento(doc, socio, "COD", codigoSocio);
                aniadirElemento(doc, socio, "NOMBRE", nombreSocio);
                aniadirElemento(doc, socio, "CUOTA_FIJA", cuotaFija);
                aniadirElemento(doc, socio, "CUOTA_TOTAL", String.valueOf(Double.parseDouble(cuotaFija) + Double.parseDouble(sumaCuotaAdicional)));
            }

            guardarDocumento(doc, "src/PracticaAdatUd6/resources/xml/archivoFinal.xml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void aniadirElemento(Document doc, Element parent, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.setTextContent(textContent);
        parent.appendChild(element);
    }
}
