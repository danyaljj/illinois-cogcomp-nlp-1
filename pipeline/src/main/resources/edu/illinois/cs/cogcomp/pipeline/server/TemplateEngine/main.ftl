<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>CogComp Pipeline Demo using Apelles</title>

    <link rel="stylesheet" type="text/css" href="./brat_client/style-vis.css"/>
    <link rel="stylesheet" type="text/css" href="./main.css"/>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-fork-ribbon-css/0.2.0/gh-fork-ribbon.min.css" />
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap-select/1.12.2/css/bootstrap-select.min.css">

    <script type="text/javascript" src="./brat_client/client/lib/head.load.min.js"></script>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.0/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/js/bootstrap.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap-select/1.12.2/js/bootstrap-select.min.js"></script>

    <script type="text/javascript">
        var bratLocation = './brat_client';

        head.js(
                // External libraries
                bratLocation + '/client/lib/jquery.min.js',
                bratLocation + '/client/lib/jquery.svg.min.js',
                bratLocation + '/client/lib/jquery.svgdom.min.js',

                // brat helper modules
                bratLocation + '/client/src/configuration.js',
                bratLocation + '/client/src/util.js',
                bratLocation + '/client/src/annotation_log.js',
                bratLocation + '/client/lib/webfont.js',

                // brat modules
                bratLocation + '/client/src/dispatcher.js',
                bratLocation + '/client/src/url_monitor.js',
                bratLocation + '/client/src/visualizer.js'
        );

        var webFontURLs = [
            // bratLocation + '/static/fonts/Astloch-Bold.ttf',
            bratLocation + '/static/fonts/PT_Sans-Caption-Web-Regular.ttf',
            bratLocation + '/static/fonts/Liberation_Sans-Regular.ttf'
        ];
    </script>
    <script src="apelles.js" type="text/javascript"></script>
</head>
<body>
<a href="https://github.com/CogComp/Apelles/" class="github-corner" aria-label="View source on Github"><svg width="80" height="80" viewBox="0 0 250 250" style="height: 13%; width: 10%; fill:#000000; color:#fff; position: absolute; top: 0; right: 0; border: 0;" aria-hidden="true"><path d="M0,0 L115,115 L130,115 L142,142 L250,250 L250,0 Z"></path><path d="M128.3,109.0 C113.8,99.7 119.0,89.6 119.0,89.6 C122.0,82.7 120.5,78.6 120.5,78.6 C119.2,72.0 123.4,76.3 123.4,76.3 C127.3,80.9 125.5,87.3 125.5,87.3 C122.9,97.6 130.6,101.9 134.4,103.2" fill="currentColor" style="transform-origin: 130px 106px;" class="octo-arm"></path><path d="M115.0,115.0 C114.9,115.1 118.7,116.5 119.8,115.4 L133.7,101.6 C136.9,99.2 139.9,98.4 142.2,98.6 C133.8,88.0 127.5,74.4 143.8,58.0 C148.5,53.4 154.0,51.2 159.7,51.0 C160.3,49.4 163.2,43.6 171.4,40.1 C171.4,40.1 176.1,42.5 178.8,56.2 C183.1,58.6 187.2,61.8 190.9,65.4 C194.5,69.0 197.7,73.2 200.1,77.6 C213.8,80.2 216.3,84.9 216.3,84.9 C212.7,93.1 206.9,96.0 205.4,96.6 C205.1,102.4 203.0,107.8 198.3,112.5 C181.9,128.9 168.3,122.5 157.7,114.1 C157.9,116.9 156.7,120.9 152.7,124.9 L141.0,136.5 C139.8,137.7 141.6,141.9 141.8,141.8 Z" fill="currentColor" class="octo-body"></path></svg></a><style>.github-corner:hover .octo-arm{animation:octocat-wave 560ms ease-in-out}@keyframes octocat-wave{0%,100%{transform:rotate(0)}20%,60%{transform:rotate(-25deg)}40%,80%{transform:rotate(10deg)}}@media (max-width:500px){.github-corner:hover .octo-arm{animation:none}.github-corner .octo-arm{animation:octocat-wave 560ms ease-in-out}}</style>
<div class="container">
    <div class="jumbotron">
        <h1>Cogcomp Pipeline Demo</h1>
        <p>
            The Illinois NLP Pipeline provides a suite of state-of-the-art Natural Language Processing tools
            that allows you to run various NLP tools to annotate plain text input.
            <button type="button" onclick="location.href='https://github.com/CogComp/cogcomp-nlp/tree/master/pipeline';" class="btn btn-danger btn-xs">Read More</button>
        </p>
    </div>
    <div style="height: 30px; margin-left: 50%">
        <div class="loader" id="loader1"></div>
        <div class="loader" id="loader2"></div>
    </div>
    <div id="main-content" style="overflow: visible;">
        <div>Enter text to annotate below (max-length: 1000 characters) </div>
        <div id="input-area" style="width: 100%" >
            <textarea maxlength="1000" rows="8" cols="80" style="width: 100%" id="viz-text">The boy gave the frog to the girl. The boy's gift was to the girl. The girl was given a frog. A squirrel is storing a lot of nuts to prepare for a seasonal change in the environment. The construction of the John Smith library finished on time.  Amanda found herself in the Winnebago with her ex-boyfriend, an herbalist and a pet detective. Rome is in Lazio province and Naples in Campania. The region at the end of 2014 had a population of around 5,869,000 people, making it the third-most-populous region of Italy. </textarea>
            <input type="submit" class="btn btn-default" id="viz-submit">
            <select class="selectpicker" id="view-selector" multiple data-actions-box="true" multiple>
                <option value="LEMMA">LEMMA</option>
                <option value="POS">POS</option>
                <option value="SHALLOW_PARSE">Shallow Parse</option>
                <option value="NER_CONLL">NER-Conll</option>
                <option value="NER_ONTONOTES">NER-Ontonotes</option>
                <option value="SRL_VERB">SRL (verb) </option>
                <option value="SRL_VERB_PATH_LSTM">SRL (Path-LSTM) </option>
                <option value="SRL_PREP">SRL (preposition) </option>
                <option value="SRL_COMMA">SRL (comma) </option>
                <option value="QUANTITIES">Quantities</option>
                <option value="DEPENDENCY">Dependency Tree (Cogcomp)</option>
                <option data-divider="true"></option>
                <option value="DEPENDENCY_STANFORD">Dependency Tree (Stanford)</option>
                <option value="STANFORD_COREF">Co-reference (Stanford) </option>
                <option value="STANFORD_RELATIONS">Relations (Stanford) </option>
                <option value="STANFORD_OPENIE">Open IE (Stanford) </option>
                <!--<option value="PARSE_STANFORD">Parse Tree (Stanford) </option>-->
            </select>
        </div>
        <div id="render-area"></div>
    </div>
    <div class="alert alert-danger" id="comeLater" style="text-align: center; display: none" role="alert">
        Servers unresponsive. Come back later!
        <div><img src="why3.png" alt="Why....." style="height: 200px; margin-top: 10px"></div>
    </div>
    <div class="alert alert-danger" id="warning" style="text-align: center; display: none" role="alert">
        Warning: external-annotations server is off Ugh.
    </div>
    <div class="alert alert-warning" style="text-align: center" role="alert">
        Problems? Use our <a href="https://github.com/CogComp/Apelles/issues">issue tracker</a>.
    </div>
</div>


<script type="text/javascript">

    // Return an array of the selected opion values
    // select is an HTML select element
    function getSelectValues(select) {
        var result = [];
        var options = select && select.options;
        var opt;

        for (var i=0, iLen=options.length; i<iLen; i++) {
            opt = options[i];

            if (opt.selected) {
                result.push(opt.value || opt.text);
            }
        }
        console.log("getSelectValues");
        console.log(result);
        return result;
    }

    fetch("http://austen.cs.illinois.edu:8080/annotate")
            .then(function() {
                console.log("Connection works. ");
            }).catch(function() {
        document.getElementById("comeLater").style.display = "block";
    });

    fetch("http://sauron.cs.illinois.edu:8080/annotate")
            .then(function() {
                console.log("Connection works. ");
            }).catch(function() {
        document.getElementById("warning").style.display = "block";
    });

    head.ready(function () {
        console.log('Loading and rendering');

        const _ = apelles.lodash;
        const brat_options = {
            'brat_util': Util,
            'brat_webFontURLs': webFontURLs
        };

        // Util.profileEnable();

        var createViewArea = function (renderDivId, viewName, viewType) {
            var outerDiv = $("<div>").attr("class", "view-area");
            outerDiv.append($("<div class='alert alert-success visualization-block' role='alert'>").text(viewName + " // " + viewType).attr("id", renderDivId));
            return outerDiv;
        };

        $("#viz-submit").click(function (eventData) {

            // loader active
            document.getElementById("loader1").style.visibility = "visible";
            document.getElementById("loader2").style.visibility = "visible";

            var pipelineViews = getSelectValues(document.getElementsByTagName('select')[0]);

            var text = $("#viz-text").val();
            console.log("Submit event: ");
            console.log(text);
            console.log($("#viz-text"));
            console.log("pipelineViews: ");
            console.log(pipelineViews);

            function sendRequest(api, loaderId) {
                var p = text ? Promise.resolve(text) : Promise.reject("Invalid Text");
                p.then(function (text) {
                    $("#render-area").empty();

                    return apelles.pipelineClient.annotateText({}, text, pipelineViews, api).then(function (jsonData) {
                        return jsonData;
                    }, function (err) {
                        // return empty annotation
                        return [];
                    });
                }).then(function (jsonData) {
                    var availableViews = apelles.getAvailableViews(jsonData);

                    var viewInfos = _.filter(availableViews, function (viewInfo) {
                        return $.inArray(viewInfo.type, apelles.supportedTypes) !== -1 && viewInfo.name !== "TOKENS";
                    });

                    _.forEach(viewInfos, function (viewInfo) {
                        //if(viewInfo.name == "DEPENDENCY_HEADFINDER:PARSE_STANFORD" || viewInfo.name == "PARSE_STANFORD") return;
                        if (!pipelineViews.includes(viewInfo.name)) return;

                        console.log("Rendering");
                        var divId = viewInfo.name;
                        var newDiv = createViewArea(divId, viewInfo.name, viewInfo.type);
                        $("#render-area").append(newDiv);

                        apelles.render(jsonData, divId, viewInfo, brat_options);
                    });
                    document.getElementById(loaderId).style.visibility = "hidden";
                }, function (err) {
                    // do nothing
                });
            }

            // main server
            sendRequest("http://austen.cs.illinois.edu:8080/annotate", "loader1");

            // external annotations server
            sendRequest("http://sauron.cs.illinois.edu:8080/annotate", "loader2");
        });
    });
</script>
</body>
</html>
