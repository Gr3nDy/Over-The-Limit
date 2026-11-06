package otl.client;

import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Default-variant metadata only; never executes gameplay rules or external XML entities. */
final class MapXml {
    final List<Element> elements=new ArrayList<>();
    final Map<String,String> constants=new LinkedHashMap<>();
    final Set<String> included=new LinkedHashSet<>();
    final Element root;
    boolean incomplete;

    MapXml(String xml,Function<String,String> includes) throws Exception {
        root=document(xml);
        collect(root,includes,0);
        for(boolean fallback:new boolean[]{false,true})for(Element e:elements)if(e.getTagName().equals("constants")&&Boolean.parseBoolean(e.getAttribute("fallback"))==fallback)
            for(Element value:children(e))if(value.getTagName().equals("constant"))constants.putIfAbsent(value.getAttribute("id"),value.getTextContent().trim());
    }

    private static Element document(String xml) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    private void collect(Element parent,Function<String,String> includes,int depth) throws Exception {
        if(depth>=16){incomplete=true;return;}
        List<String> pending=new ArrayList<>();
        for(Element e:children(parent)){
            String name=e.getTagName();
            if(name.equals("if")||name.equals("unless")){
                // shortcut: preview the default variant; server-selected variants need authoritative server metadata.
                if(e.hasAttribute("variant")){
                    boolean matches=Arrays.asList(e.getAttribute("variant").split("[ ,]+")).contains("default");
                    if(matches!=name.equals("unless"))collect(e,includes,depth+1);
                }
            }else if(name.equals("include"))pending.add(e.getAttribute("id"));
            else elements.add(e);
        }
        for(String id:pending){
            if(included.contains(id))continue;
            if(depth>=8||included.size()>=32||!id.matches("[A-Za-z0-9_-]+")){incomplete=true;continue;}
            included.add(id);
            String xml=includes.apply(id);
            if(xml==null){incomplete=true;continue;}
            collect(document(xml),includes,depth+1);
        }
    }

    static List<Element> children(Element parent){
        List<Element> out=new ArrayList<>();
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element)out.add((Element)n);
        return out;
    }

    Element first(String name){for(Element e:elements)if(e.getTagName().equals(name))return e;return null;}
    String value(String name){Element e=first(name);return e==null?"":expand(e.getTextContent().trim());}

    String expand(String value){
        Pattern variable=Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)\\}");
        for(int i=0;i<8;i++){
            Matcher m=variable.matcher(value);StringBuffer result=new StringBuffer();boolean changed=false;
            while(m.find()){
                String replacement=constants.get(m.group(1));
                if(replacement!=null){m.appendReplacement(result,Matcher.quoteReplacement(replacement));changed=true;}
                else m.appendReplacement(result,Matcher.quoteReplacement(m.group()));
            }
            m.appendTail(result);value=result.toString();if(!changed)break;
        }
        if(variable.matcher(value).find()){incomplete=true;return variable.matcher(value).replaceAll("server-defined");}
        return value;
    }

    String people(String section,String element){
        Element block=first(section);
        if(block==null||block.getOwnerDocument()!=root.getOwnerDocument())return "";
        List<String> names=new ArrayList<>();
        for(Element e:children(block))if(e.getTagName().equals(element)){
            String name=e.getTextContent().trim();
            Node next=e.getNextSibling();while(next!=null&&next.getNodeType()==Node.TEXT_NODE&&next.getTextContent().trim().isEmpty())next=next.getNextSibling();
            if(name.isEmpty()&&next!=null&&next.getNodeType()==Node.COMMENT_NODE)name=next.getNodeValue().trim();
            if(name.isEmpty())name=e.getAttribute("name");
            if(name.isEmpty()){name=e.getAttribute("uuid");if(name.length()==36)name=name.substring(0,8);}
            if(name.isEmpty())continue;
            String role=e.getAttribute("contribution");
            names.add(name+(role.isEmpty()?"":" ("+role+")"));
        }
        return String.join(", ",names);
    }
}
