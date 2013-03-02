
function initSearch() {
  jQuery.noConflict();

  (function($){
    //*** Global variables ***
    var CONNECTORS;
    var SEARCH_TYPES;
    var SEARCH_SETTING;
    var LIMIT;

    var RESULT_CACHE, CACHE_OFFSET, SERVER_OFFSET, NUM_RESULTS_RENDERED;

    var SEARCH_RESULT_TEMPLATE = " \
      <div class='SearchResult %{type}'> \
        <div class='Avatar Clickable'> \
          %{avatar} \
        </div> \
        <div class='Content'> \
          <div class='Title Ellipsis'><a href='%{url}'>%{title}</a></div> \
          <div class='Excerpt Ellipsis'>%{excerpt}</div> \
          <div class='Detail'>%{detail}</div> \
        </div> \
      </div> \
    ";


    //*** Utility functions ***

    String.prototype.toProperCase = function() {
      return this.replace(/\w\S*/g, function(txt){return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();});
    };


    String.prototype.highlight = function(words) {
      var str = this;
      for(var i=0; i<words.length; i++) {
        if(""==words[i]) continue;
        var regex = new RegExp("(" + words[i] + ")", "gi");
        str = str.replace(regex, "<strong>$1</strong>");
      }
      return str;
    };


    function setWaitingStatus(status) {
      if(status) {
        $("body").css("cursor", "wait");
        $("#searchPortlet").css({"pointer-events":"none"});
      } else {
        $("body").css("cursor", "auto");
        $("#searchPortlet").css({"pointer-events":"auto"});
      }
    }


    function getUrlParam(name) {
      var match = RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
      return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
    }


    function getRegistry(callback) {
      $.getJSON("/rest/search/registry", function(registry){
        if(callback) callback(registry);
      });
    }


    function getSearchSetting(callback) {
      $.getJSON("/rest/search/setting", function(setting){
        if(callback) callback(setting);
      });
    }


    function loadContentFilter(connectors, searchSetting) {
      var contentTypes = [];
      $.each(SEARCH_TYPES, function(i, searchType){
        var connector = connectors[searchType];
        // Show only the types user selected in setting
        if(connector && (-1 != $.inArray("all", searchSetting.searchTypes) || -1 != $.inArray(searchType, searchSetting.searchTypes))) {
          contentTypes.push("<li><span class='uiCheckbox'><input type='checkbox' class='checkbox' name='contentType' value='" + connector.searchType + "'><span></span></span>" + connector.displayName + "</li>");
        }
      });
      if(0!=contentTypes.length) {
        $("#lstContentTypes").html(contentTypes.join(""));
      } else {
        $(":checkbox[name='contentType'][value='all']").attr("checked", false).attr("disabled", "disabled");
      }
    }


    function loadSiteFilter(searchSetting, callback) {
      if(searchSetting.searchCurrentSiteOnly) {
        $("#siteFilter").hide();
      } else {
        $.getJSON("/rest/search/sites", function(sites){
          var siteNames = [];
          $.each(sites, function(i, site){
            siteNames.push("<li><span class='uiCheckbox'><input type='checkbox' class='checkbox' name='site' value='" + site + "'><span></span></span>" + site.toProperCase() + "</li>");
          });
          $("#lstSites").html(siteNames.join(""));
          if(callback) callback();
        });
      }
    }


    function getSelectedTypes(){
      var selectedTypes = [];
      $.each($(":checkbox[name='contentType'][value!='all']:checked"), function(){
        selectedTypes.push(this.value);
      });
      return selectedTypes.join(",");
    }


    function getSelectedSites(){
      if(SEARCH_SETTING.searchCurrentSiteOnly) return getUrlParam("currentSite") || parent.eXo.env.portal.portalName;
      var selectedSites = [];
      $.each($(":checkbox[name='site'][value!='all']:checked"), function(){
        selectedSites.push(this.value);
      });
      return selectedSites.join(",");
    }


    function renderSearchResult(result) {
      var query = $("#txtQuery").val();
      var terms = query.split(/\s+/g);

      var avatar = "<img src='"+result.imageUrl+"' alt='"+ result.imageUrl+"'>";

      if("event"==result.type || "task"==result.type) {
        result.url = "/portal/intranet/calendar" + result.url;
      }

      if("event"==result.type) {
        var date = new Date(result.fromDateTime).toString().split(/\s+/g);
        avatar = " \
          <div class='calendarBox'> \
            <div class='heading' style='padding-top: 0px; padding-bottom: 0px; border-width: 0px;'>" + date[1] + "</div> \
            <div class='content' style='padding: 0px 6px; padding-bottom: 0px; border-top-width: 0px;'>" + date[2] + "</div> \
          </div> \
        ";
      }

      if ("task"==result.type){
        avatar = "\
          <div class='statusTask'>\
            <i class='"+result.imageUrl+"Icon'></i>\
          </div>\
        ";
      }

      var html = SEARCH_RESULT_TEMPLATE.
        replace(/%{type}/g, result.type).
        replace(/%{url}/g, result.url).
        replace(/%{title}/g, (result.title||"").highlight(terms)).
        replace(/%{excerpt}/g, (result.excerpt||"").highlight(terms)).
        replace(/%{detail}/g, (result.detail||"").highlight(terms)).
        replace(/%{avatar}/g, avatar);

      $("#result").append(html);
    }


    function clearResultPage(message){
      $("#result").html("");
      $("#resultHeader").html(message?message:"");
      $("#resultSort").hide();
      $("#showMore").hide();
      setWaitingStatus(false);
      return;
    }


    // Client-side sort functions
    function byRelevancyASC(a,b) {
      if (a.relevancy < b.relevancy)
        return -1;
      if (a.relevancy > b.relevancy)
        return 1;
      return 0;
    }
    function byRelevancyDESC(b,a) {
      if (a.relevancy < b.relevancy)
        return -1;
      if (a.relevancy > b.relevancy)
        return 1;
      return 0;
    }

    function byDateASC(a,b) {
      if (a.date < b.date)
        return -1;
      if (a.date > b.date)
        return 1;
      return 0;
    }
    function byDateDESC(b,a) {
      if (a.date < b.date)
        return -1;
      if (a.date > b.date)
        return 1;
      return 0;
    }

    function byTitleASC(a,b) {
      if ((a.title||"").toUpperCase() < (b.title||"").toUpperCase())
        return -1;
      if ((a.title||"").toUpperCase() > (b.title||"").toUpperCase())
        return 1;
      return 0;
    }
    function byTitleDESC(b,a) {
      if ((a.title||"").toUpperCase() < (b.title||"").toUpperCase())
        return -1;
      if ((a.title||"").toUpperCase() > (b.title||"").toUpperCase())
        return 1;
      return 0;
    }


    function search(callback) {
      SERVER_OFFSET = 0;
      NUM_RESULTS_RENDERED = 0;

      getFromServer(function(){
        renderCachedResults();
      });
    }


    function getFromServer(callback){
      var query = $("#txtQuery").val();
      if(""==query) {
        clearResultPage();
        return;
      }

      var sort = $("#sortField").text();
      var order = $("#sortField").attr("order");

      setWaitingStatus(true);

      var restUrl = "/rest/search?q="+ query+"&sites="+getSelectedSites()+"&types="+getSelectedTypes()+"&offset="+SERVER_OFFSET+"&limit="+LIMIT+"&sort="+sort+"&order="+order;
      $.getJSON(restUrl, function(resultMap){
        RESULT_CACHE = [];
        $.each(resultMap, function(searchType, results){
          results.map(function(result){result.type = searchType;});
          RESULT_CACHE.push.apply(RESULT_CACHE, results);
        });

        var sortFuncName = "by" + sort.toProperCase() + order.toUpperCase();
        RESULT_CACHE = RESULT_CACHE.sort(eval(sortFuncName)); //sort the result set

        CACHE_OFFSET = 0; //reset the local offset

        if(callback) callback();
        if(RESULT_CACHE.length < LIMIT) $("#showMore").hide(); else $("#showMore").show();
        setWaitingStatus(false);
      });
    }


    function renderCachedResults(append) {
      var current = RESULT_CACHE.slice(CACHE_OFFSET, CACHE_OFFSET+LIMIT);
      if(0==current.length) {
        if(append) {
          $("#showMore").hide();
        } else {
          clearResultPage("No result for <strong>" + $("#txtQuery").val() + "<strong>");
        }
        return;
      }

      NUM_RESULTS_RENDERED = NUM_RESULTS_RENDERED + current.length;
      var resultHeader = "Results " + 1 + " to " + NUM_RESULTS_RENDERED + " for <strong>" + $("#txtQuery").val() + "<strong>";
      $("#resultHeader").html(resultHeader);
      $("#resultSort").show();

      if(!append) $("#result").html("");
      $.each(current, function(i, result){
        renderSearchResult(result);
      });
    }


    //*** Event handlers ***

    $("#btnShowMore").click(function(){
      CACHE_OFFSET = CACHE_OFFSET + LIMIT;
      var remaining = RESULT_CACHE.slice(CACHE_OFFSET, CACHE_OFFSET+LIMIT);

      if(remaining.length < LIMIT) {
        SERVER_OFFSET = SERVER_OFFSET + LIMIT;
        getFromServer(function(){
          RESULT_CACHE = remaining.concat(RESULT_CACHE);
          renderCachedResults(true);
          $("#searchPage").animate({ scrollTop: $("#resultPage")[0].scrollHeight}, "slow");
        });
        return;
      }
      renderCachedResults(true);
      $("#searchPage").animate({ scrollTop: $("#resultPage")[0].scrollHeight}, "slow");
    });


    $(":checkbox[name='contentType']").live("click", function(){
      if("all"==this.value){ //All Content Types checked
        if($(this).is(":checked")) { // check/uncheck all
          $(":checkbox[name='contentType']").attr('checked', true);
        } else {
          $(":checkbox[name='contentType']").attr('checked', false);
        }
      } else {
        $(":checkbox[name='contentType'][value='all']").attr('checked', false); //uncheck All Content Types
      }

      search();
    });


    $(":checkbox[name='site']").live("click", function(){
      if("all"==this.value){ //All Sites checked
        if($(this).is(":checked")) { // check/uncheck all
          $(":checkbox[name='site']").attr('checked', true);
        } else {
          $(":checkbox[name='site']").attr('checked', false);
        }
      } else {
        $(":checkbox[name='site'][value='all']").attr('checked', false); //uncheck All Sites
      }

      search();
    });


    $("#btnSearch").click(function(){
      search();
    });


    $("#txtQuery").keyup(function(e){
      var keyCode = e.keyCode || e.which;
      if(13==keyCode) search();
    });


    $("#sortOptions > li > a").on("click", function(){
      var oldOption = $("#sortField").text();
      var newOption = $(this).text();

      if(newOption==oldOption) { //click a same option again
        $(this).children("i").toggleClass("uiIconSortUp uiIconSortDown"); //toggle the arrow
      } else {
        $("#sortField").text(newOption);
        $("#sortOptions > li > a > i").remove(); //remove the arrows from other options

        // Select the default sort order: DESC for Relevancy, ASC for Date & Title
        var sortByIcon;
        switch(newOption) {
          case "Relevancy":
            sortByIcon = 'uiIconSortDown';
            break;
          case "Date":
            sortByIcon = 'uiIconSortUp';
            break;
          case "Title":
            sortByIcon = 'uiIconSortUp';
            break;
        }

        $(this).append("<i class='" + sortByIcon + "'></i>"); //add the arrow to this option
      }

      $("#sortField").attr("order", $(this).children("i").hasClass("uiIconSortUp") ? "asc" : "desc");

      SERVER_OFFSET = 0;
      NUM_RESULTS_RENDERED = 0;
      getFromServer(function(){
        renderCachedResults();
      });

    });


    //*** The entry point ***
    getRegistry(function(registry){
      CONNECTORS = registry[0];
      SEARCH_TYPES = registry[1];
      getSearchSetting(function(setting){
        SEARCH_SETTING = setting;

        loadContentFilter(CONNECTORS, setting);
        loadSiteFilter(setting, function(){
          var sites = getUrlParam("sites");
          if(sites) {
            $.each($(":checkbox[name='site']"), function(){
              $(this).attr('checked', -1!=sites.indexOf(this.value) || -1!=sites.indexOf("all"));
            });
          } else {
            $(":checkbox[name='site']").attr('checked', true);  //check all sites by default
          }

          if(query && !setting.searchCurrentSiteOnly) search();
        });

        if(!setting.hideFacetsFilter) {
          $("#facetsFilter").show();
        }

        if(!setting.hideSearchForm) {
          $("#searchForm").show();
          $("#txtQuery").focus();
        }

        var query = getUrlParam("q");
        $("#txtQuery").val(query);

        var types = getUrlParam("types");
        if(types) {
          $.each($(":checkbox[name='contentType']"), function(){
            $(this).attr('checked', -1!=types.indexOf(this.value) || -1!=types.indexOf("all"));
          });
        } else {
          $(":checkbox[name='contentType']").attr('checked', true); //check all types by default
        }

        $("#sortField").text((getUrlParam("sort")||"relevancy").toProperCase());
        $("#sortField").attr("order", getUrlParam("order") || "desc");

        var limit = getUrlParam("limit");
        LIMIT = limit && !isNaN(parseInt(limit)) ? parseInt(limit) : setting.resultsPerPage;

        $("#txtQuery").focus();

        if(query && setting.searchCurrentSiteOnly) search();

      });
    });

  })(jQuery);

  $ = jQuery; //undo .conflict();
}