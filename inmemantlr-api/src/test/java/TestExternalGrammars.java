/**
 * Inmemantlr - In memory compiler for Antlr 4
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Julian Thome <julian.thome.de@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snt.inmemantlr.GenericParser;
import org.snt.inmemantlr.exceptions.CompilationException;
import org.snt.inmemantlr.exceptions.IllegalWorkflowException;
import org.snt.inmemantlr.exceptions.ParsingException;
import org.snt.inmemantlr.listener.DefaultTreeListener;
import org.snt.inmemantlr.stream.CasedStreamProvider;
import org.snt.inmemantlr.tool.ToolCustomizer;
import org.snt.inmemantlr.tree.ParseTree;
import org.snt.inmemantlr.utils.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;



public class TestExternalGrammars {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExternalGrammars.class);

    private static String[] special = {
            "antlr4", // antlr4 is handled by an extra test case
            "aspectj", // have to manually adapt Java grammar
            "csharp", // csharp is handled by an extra test case
            "objc", // objc is handled by an extra test case
            "php",  // php is handled by an extra test case
            "stringtemplate",  // stringtemplate is handled by an extra case
            "python2", // seems to be broken
            "swift2", // handled by extra testcase
            "swift3", // handled by extra testcase
            "swift-fin",
            "z", // handled by extra testcase
            "tsql", // handled by extra testcase
            "plsql", // handled by extra testcase
            "mysql", // handled by extra testcase
            "html", // handled by extra testcase
            "r", // handled by extra testcase

            "javascript", // skip
            "antlr3", // skip
            "python3alt", //skip
            "python3-py",// skip"  - @TODO: investigate this one
            "solidity" //skip - @TODO: investigate this one
    };



    static File grammar = null;

    private Set<String> specialCases = new HashSet(Arrays.asList(special));

    private static Map<String, Subject> subjects = new HashMap<>();

    private static class Subject {

        public String name = "";
        public Set<File> g4 = new HashSet();
        // positive examples
        public Set<File> examples = new HashSet();

        // negative examples
        public Set<File> nexamples = new HashSet();
        public Map<String, String> errors = new HashMap();

        private String entrypoint = "";

        public boolean hasExamples() {
            return !examples.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name:");
            sb.append(name);
            sb.append("\n");
            sb.append("g4 files:\n");
            g4.forEach(file -> {
                sb.append(file.getAbsolutePath());
                sb.append("\n");
            });
            if (hasExamples()) {
                sb.append("examples:\n");
                examples.forEach(file -> {
                    sb.append(file.getAbsolutePath());
                    sb.append("\n");
                });
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    private GenericParser getParserForSubject(Subject s, ToolCustomizer tc) {


        File [] gs = s.g4.toArray(new File [s.g4.size()]);

        LOGGER.debug("gs {}" ,gs.length);

        GenericParser gp = null;
        try {
            gp = new GenericParser(tc, gs);
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }

        Assertions.assertNotNull(gp);

        return gp;
    }

    private void verify(GenericParser g, Set<File> ok, Set<File> error) {
        verify(g, ok, null, GenericParser.CaseSensitiveType.NONE, true);
        verify(g, error, null, GenericParser.CaseSensitiveType.NONE, false);
    }

    private void verify(GenericParser p, Set<File> ok, Set<File> error, String ep) {
        DefaultTreeListener dt = new DefaultTreeListener();
        verify(p, ok, ep, GenericParser.CaseSensitiveType.NONE,true);
        verify(p, error, ep, GenericParser.CaseSensitiveType.NONE, false);
    }

    private void verify(GenericParser p, Set<File> toParse, String ep,
                        GenericParser.CaseSensitiveType t, boolean shouldParse) {
        DefaultTreeListener dt = new DefaultTreeListener();
        p.setListener(dt);

        boolean parses;

        for(File e : toParse){
            try {
                LOGGER.info("parseFile {} with {} and ept {}", e.getName(), p
                        .getParserName(), ep);
                ParserRuleContext ctx = (ep != null && !ep.isEmpty()) ? p
                        .parse(e,ep, GenericParser.CaseSensitiveType.NONE) :
                        p.parse(e, t);
                //Assert.Assertions.assertNotNull(ctx);
                if(ctx == null)
                    parses = false;
                else
                    parses = true;
            } catch (IllegalWorkflowException | FileNotFoundException |
                    RecognitionException | ParsingException e1) {
                LOGGER.error("error: {}", e1.getMessage());
                parses = false;
            }

            Assertions.assertEquals(shouldParse, parses);


            if(shouldParse) {
                ParseTree parseTree = dt.getParseTree();
                Assertions.assertNotNull(parseTree);
                LOGGER.debug(parseTree.toDot());
                Assertions.assertTrue(parseTree.getNodes().size() > 1);
            }
        }
    }

    static {
        if (TestExternalGrammars.class.getClassLoader().getResource("grammars-v4") == null)
           Assertions.assertFalse(true);

        grammar = new File(TestExternalGrammars.class.getClassLoader().getResource("grammars-v4").getFile());

        File[] files = grammar.listFiles(File::isDirectory);


        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();


        for (File f : files) {


            Assertions.assertTrue(f.isDirectory());
            Subject subject = new Subject();
            subject.name = f.getName();


            File [] xmla = f.listFiles(pathname -> pathname
                            .getName()
                            .equals("pom.xml"));

            List<File> xmls = Arrays.asList(xmla);

            if(xmls.size() == 1) {
                Document doc = null;
                try {
                    doc = dbf.newDocumentBuilder().parse(xmls.get(0));
                } catch (ParserConfigurationException e) {
                    Assertions.assertTrue(false);
                } catch (SAXException e) {
                    Assertions.assertTrue(false);
                } catch (IOException e) {
                    Assertions.assertTrue(false);
                }

                NodeList nl = doc.getElementsByTagName("entryPoint");

                if(nl.getLength() == 1) {
                    subject.entrypoint = nl.item(0).getTextContent();
                }

            }


            File[] gs = f.listFiles(pathname -> pathname.getName().endsWith(".g4"));


            if (gs != null && gs.length > 0) {
                subject.g4.addAll(Arrays.asList(gs));
            }

            File examples = new File(f.getAbsolutePath() + "/examples");

            File[] xamples = examples.listFiles(pathname -> !pathname
                    .isDirectory() && !FilenameUtils.getExtension(pathname
                    .getName()).equals("tree") &&
                    !FilenameUtils.getExtension(pathname.getName()).equals("errors") &&
                    !FilenameUtils.getName(pathname.getName()).contains
                            ("form1.vb")

            );

            File[] errors = examples.listFiles(pathname -> !pathname
                    .isDirectory() &&
                    FilenameUtils.getExtension(pathname.getName()).equals("errors")
            );

            if(errors != null) {
                for (File ef : errors) {
                    String content = FileUtils.loadFileContent(ef);
                    subject.errors.put(FilenameUtils.getBaseName(ef.getName()), content);
                }
            }

            if (xamples != null && xamples.length > 0) {
                subject.examples.addAll(Arrays.asList(xamples));
                Set<File> negative = subject.examples.stream().filter(x ->
                        subject.errors.keySet().contains(x.getName()))
                        .collect(Collectors.toSet());
                subject.examples.removeAll(negative);
                subject.nexamples.addAll(negative);
            }


            subjects.put(subject.name, subject);
        }
    }

    private void testSubject(Subject s, boolean skip) {
        LOGGER.debug("test {}", s.name);
        if (specialCases.contains(s.name) && skip) {
            LOGGER.debug("skip {}", s.name);
            return;
        }

        LOGGER.info("test {}", s.name);
        GenericParser gp = getParserForSubject(s, null);

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            LOGGER.debug("Error for {}:{}", s.name, e.getMessage());
            compile = false;
        }

        Assertions.assertTrue(compile);
        LOGGER.debug("successfully compiled grammar");

        DefaultTreeListener dt = new DefaultTreeListener();
        gp.setListener(dt);

        verify(gp, s.examples, s.nexamples, s.entrypoint);

    }

    @Test
    public void testGeneration() {
        subjects.values().stream().filter(Subject::hasExamples).
                    forEach(s -> testSubject(s,true));
    }

    private boolean toCheck(String tcase) {
        if(subjects.containsKey(tcase))
            return true;

        return false;
    }

    @Test
    public void testAntlr4() {

        if (!toCheck("antlr4"))
            return;

        Subject s = subjects.get("antlr4");

        // Exam
        ToolCustomizer tc = t -> t.genPackage = "org.antlr.parser.antlr4";

        Set<File> files = s.g4.stream().filter(v -> v.getName().matches("" +
                "(ANTLRv4" +
                "(Lexer|Parser)|LexBasic).g4")).collect(Collectors.toSet());

        Assertions.assertTrue(files.size() > 0);

        GenericParser gp = null;
        try {
            gp = new GenericParser(tc, files.toArray(new File[files
                    .size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }

        DefaultTreeListener dt = new DefaultTreeListener();

        gp.setListener(dt);

        try {
            File util = new File
                    ("src/test/resources/grammars-v4/antlr4/src/main/java" +
                            "/org" +
                            "/antlr/parser/antlr4/LexerAdaptor.java");
            gp.addUtilityJavaFiles(util);
        } catch (FileNotFoundException e) {
            Assertions.assertFalse(true);
        }

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        s.examples.removeIf(f -> f.getName().contains("three.g4"));

        Assertions.assertTrue(compile);

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testStringTemplate() {

        // skip for the time being -- grammar issue
        if(true)
            return;


        if (!toCheck("stringtemplate"))
            return;

        Subject s = subjects.get("stringtemplate");

        //LOGGER.info("G4 {}", s.g4);

        // Exam
        ToolCustomizer tc = t -> t.genPackage = "org.antlr.parser.st4";

        GenericParser gp = getParserForSubject(s, tc);

        DefaultTreeListener dt = new DefaultTreeListener();

        gp.setListener(dt);


        try {
            File util = new File
                    ("src/test/resources/grammars-v4/stringtemplate/" +
                            "src/main/java/org/antlr/parser/st4/LexerAdaptor.java");
            gp.addUtilityJavaFiles(util);
        } catch (FileNotFoundException e) {
            Assertions.assertFalse(true);
        }

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }


        LOGGER.debug("name {}", gp.getParserName());
        gp.setParserName("org.antlr.parser.st4.STParser");
        gp.setLexerName("org.antlr.parser.st4.STLexer");
        Assertions.assertTrue(compile);

        s.examples = s.examples.stream().filter( f -> f.getName().contains
                ("example1.st")
        ).collect(Collectors.toSet());

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testSwift2() {

        if (!toCheck("swift2"))
            return;

        Subject s = subjects.get("swift2");

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        try {
            File util = new File
                    ("src/test/resources/grammars-v4/swift2/src/main/java" +
                            "/SwiftSupport.java");
            gp.addUtilityJavaFiles(util);
        } catch (FileNotFoundException e) {
            Assertions.assertFalse(true);
        }

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        Assertions.assertTrue(compile);

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testSwift3() {

        if (!toCheck("swift3"))
            return;

        Subject s = subjects.get("swift3");

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        try {
            File util = new File
                    ("src/test/resources/grammars-v4/swift3/src/main/java" +
                            "/SwiftSupport.java");
            gp.addUtilityJavaFiles(util);
        } catch (FileNotFoundException e) {
            Assertions.assertFalse(true);
        }

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        Assertions.assertTrue(compile);

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }



    @Test
    public void testEcma() {

        if (!toCheck("ecmascript"))
            return;

        Subject s = subjects.get("ecmascript");

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        Assertions.assertTrue(compile);

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }




    @Test
    public void testPHP() {


        if (!toCheck("php"))
            return;

        Subject s = subjects.get("php");

        s.g4.removeIf(f -> f.getName().equals("PHPLexer_CSharpSharwell.g4") ||
                f.getName().equals("PHPLexer_Python.g4"));

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }


        gp.setStreamProvider(new CasedStreamProvider(GenericParser
                .CaseSensitiveType.LOWER));

        Assertions.assertTrue(compile);


        s.examples = s.examples.stream().filter( f -> !f.getName().contains
                ("alternativeSyntax.php")
        ).collect(Collectors.toSet());

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }



    @Test
    public void testZ() {

        if (!toCheck("z"))
            return;
        Subject s = subjects.get("z");


        //Assertions.assertTrue(mfiles.size() > 0);

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        try {
            File util1 = new File
                    ("src/test/resources/grammars-v4/z/src/main/java" +
                            "/ZOperatorListener.java");
            File util2 = new File
                    ("src/test/resources/grammars-v4/z/src/main/java" +
                            "/ZSupport.java");

            gp.addUtilityJavaFiles(util1, util2);

        } catch (FileNotFoundException e) {
            Assertions.assertFalse(true);
        }

        Assertions.assertNotNull(gp);

        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        gp.setListener(mdt);

        Assertions.assertTrue(compile);

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }


    @Test
    public void testObjc() {

        if (!toCheck("objc"))
            return;

        Subject s = subjects.get("objc");

        s.g4.add(new File("src/test/resources/grammars-v4/objc/one-step" +
                "-processing/ObjectiveCLexer.g4"));
        s.g4.add(new File("src/test/resources/grammars-v4/objc/one-step" +
                "-processing/ObjectiveCPreprocessorParser.g4"));

        GenericParser gp = null;
        try {
            gp = new GenericParser(s.g4.toArray(new File[s.g4.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            LOGGER.error(e.getMessage());
            compile = false;
        }

        gp.setListener(mdt);

        Assertions.assertTrue(compile);

        s.examples.removeIf(p -> p.getName().contains("AllInOne.m"));

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }


    @Test
    public void testCSharp() {

        // skipt this test if the runtime environment is windows for the time
        // being -- csharp grammar is runtime dependent
        if(System.getProperty("os.name").toLowerCase().startsWith("win"))
            return;

        if (!toCheck("csharp"))
            return;

        Subject s = subjects.get("csharp");

        Set<File> mfiles = s.g4.stream().filter(v -> v.getName().matches(
                "CSharp" + "(Lexer|PreprocessorParser|Parser).g4")).collect
                (Collectors.toSet());

        Assertions.assertTrue(mfiles.size() > 0);

        GenericParser mparser = null;
        try {
            mparser = new GenericParser(mfiles.toArray(new File[mfiles.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        Assertions.assertNotNull(mparser);

        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            mparser.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        mparser.setListener(mdt);

        mparser.setParserName("CSharpParser");

        Assertions.assertTrue(compile);

        verify(mparser, s.examples, s.nexamples, s.entrypoint);
    }



    @Test
    public void testTSql() {

        if (!toCheck("tsql"))
            return;

        Subject s = subjects.get("tsql");

        Set<File> mfiles = s.g4.stream().filter(v -> v.getName().matches(
                "TSql" + "(Lexer|Parser).g4")).collect
                (Collectors.toSet());

        Assertions.assertTrue(mfiles.size() > 0);

        GenericParser mparser = null;
        try {
            mparser = new GenericParser(mfiles.toArray(new File[mfiles.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        Assertions.assertNotNull(mparser);

        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            mparser.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        mparser.setStreamProvider(new CasedStreamProvider(GenericParser
                        .CaseSensitiveType.UPPER));

        mparser.setListener(mdt);

        Assertions.assertTrue(compile);

        // seems to cause issues when running on windows
        s.examples.removeIf(f -> f.getName().equals("full_width_chars.sql"));

        verify(mparser, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testPlsql() {


        if (!toCheck("plsql"))
            return;

        Subject s = subjects.get("plsql");

        Set<File> mfiles = s.g4.stream().filter(v -> v.getName().matches(
                "PlSql" + "(Lexer|Parser).g4")).collect
                (Collectors.toSet());

        Assertions.assertTrue(mfiles.size() > 0);

        GenericParser mparser = null;
        try {
            mparser = new GenericParser(mfiles.toArray(new File[mfiles.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        Assertions.assertNotNull(mparser);

        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            mparser.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        mparser.setStreamProvider(new CasedStreamProvider(GenericParser
                .CaseSensitiveType.UPPER));

        mparser.setListener(mdt);

        Assertions.assertTrue(compile);

        verify(mparser, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testMySQL() {

        if (!toCheck("mysql"))
            return;

        Subject s = subjects.get("mysql");

        Set<File> mfiles = s.g4.stream().filter(v -> v.getName().matches(
                "MySql" + "(Lexer|Parser).g4")).collect
                (Collectors.toSet());

        Assertions.assertTrue(mfiles.size() > 0);

        GenericParser mparser = null;
        try {
            mparser = new GenericParser(mfiles.toArray(new File[mfiles.size()]));
        } catch (FileNotFoundException e) {
            Assertions.assertTrue(false);
        }


        Assertions.assertNotNull(mparser);

        DefaultTreeListener mdt = new DefaultTreeListener();

        boolean compile;
        try {
            mparser.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        mparser.setStreamProvider(new CasedStreamProvider(GenericParser
                .CaseSensitiveType.UPPER));

        mparser.setListener(mdt);

        Assertions.assertTrue(compile);

        verify(mparser, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testHTML() {

        if (!toCheck("html"))
            return;

        Subject s = subjects.get("html");

        GenericParser gp = getParserForSubject(s, null);

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        Assertions.assertTrue(compile);

        s.examples.removeIf(p -> !p.getName().contains("antlr"));
        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testR() {

        if (!toCheck("r"))
            return;

        Subject s = subjects.get("r");

        GenericParser gp = getParserForSubject(s, null);

        boolean compile;
        try {
            gp.compile();
            compile = true;
        } catch (CompilationException e) {
            compile = false;
        }

        Assertions.assertTrue(compile);

        gp.setParserName("RParser");

        verify(gp, s.examples, s.nexamples, s.entrypoint);
    }

    @Test
    public void testJavascript() {

//        if (!toCheck("javascript"))
//            return;
//
//        Subject s = subjects.get("javascript");
//
//        Set<File> mfiles = s.g4.stream().filter(v -> v.getName().matches(
//                "JavaScript" + "(Lexer|Parser).g4")).collect
//                (Collectors.toSet());
//
//        Assertions.assertTrue(mfiles.size() > 0);
//
//        GenericParser jParser = null;
//        try {
//            jParser = new GenericParser(mfiles.toArray(new File[mfiles.size()]));
//        } catch (FileNotFoundException e) {
//            Assertions.assertTrue(false);
//        }
//
//        jParser.setParserName("JavaScriptParser");
//        jParser.setLexerName("JavaScriptLexer");
//
//
//        File util1 = new File
//                ("src/test/resources/grammars-v4/javascript/Java" +
//                        "/JavaScriptBaseLexer.java");
//        File util2 = new File
//                ("src/test/resources/grammars-v4/javascript/Java" +
//                        "/JavaScriptBaseParser.java");
//
//        try {
//            jParser.addUtilityJavaFiles(util1, util2);
//        } catch (FileNotFoundException e) {
//            LOGGER.error(e.getMessage());
//            Assertions.assertTrue(false);
//        }
//
//
//        Assertions.assertNotNull(jParser);
//
//        DefaultTreeListener mdt = new DefaultTreeListener();
//
//        boolean compile;
//        try {
//            jParser.compile();
//            compile = true;
//        } catch (CompilationException e) {
//            LOGGER.error(e.getMessage());
//            compile = false;
//        }
//
//
//        jParser.setListener(mdt);
//
//
//        Assertions.assertTrue(compile);
//
//        verify(jParser, s.examples, s.nexamples, "program");
    }

}
