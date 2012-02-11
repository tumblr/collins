/**
* Given an input element, find the next input in the form and focus it
*/
function focusNextInput(e) {
  // Use an :eq selector to find the input having an index one more than the current element
  var next = $(":input:eq(" + ($(":input").index(e) + 1) + ")");
  next.focus();
}

$(document).ready(function() {

  // Setup alert boxes to be dismissable
  $(".alert-message[data-alert]").alert();

  // focus input elements with a focus class
  $("input.focus").focus();

  $("input[data-type=date]").dateinput({
    format: 'yyyy-mm-dd',
    change: function() {
      var isoDate = this.getValue('yyyy-mm-dd') + 'T00:00:00'
      this.getInput().val(isoDate);
    }
  });

  // Attach a keypress handler that moves to the next input on enter
  $("input[enter-style=tab]").each(function() {
    var e = $(this);
    e.keypress(function(event) {
      if (event.which == 10 || event.which == 13) {
        event.preventDefault();
        focusNextInput(event.target);
      }
    });
  });

  $("[alt-key]").each(function() {
    var e = $(this);
    var altKey = e.attr('alt-key').toLowerCase();
    var keyLen = altKey.length;
    var soFar = "";
    $(document).keypress(function(event) {
      if (event.which == 10 || event.which == 13) {
        if (soFar.slice(soFar.length - keyLen).toLowerCase() == altKey) {
          window.location=$(e).attr('href')
        }
      } else {
        soFar = soFar + String.fromCharCode(event.which);
      }
    });
  });

  $("button[data-close]").each(function() {
    var e = $(this);
    var toClose = $(e.attr('data-close'));
    toClose.bind('hide', function() {
      $('.addedError').each(function(e) {
        $(this).text("");
      });
    });
    e.click(function() {
      toClose.modal('hide');
    });
  });

  $("button[data-remote]").each(function() {
    var e = $(this);
    var remoteUrl = e.attr('data-remote');
    var method = e.attr('data-method') || 'GET';
    var bodyId = e.attr('data-body');
    var errorArea = e.attr('data-error');
    var ajaxArgs = {
      type: method,
      url: remoteUrl
    }
    ajaxArgs.success = function(o) {
      window.location.reload();
    }
    ajaxArgs.error = function(o) {
      if (errorArea) {
        var msg = JSON.parse(o.responseText).data.message;
        $(errorArea).html('<span class="addedError">' + msg + '</span>');
      }
    }
    e.click(function() {
      if (bodyId) {
        console.log("Body ID is " + bodyId);
        body = {}
        var bodyHash = "#" + bodyId;
        console.log(bodyHash);
        body[bodyId] = $(bodyHash).val()
        console.log($(bodyHash));
        console.log(body);
        ajaxArgs.data = body
        console.log(ajaxArgs);
      }
      $.ajax(ajaxArgs)
    });
  });

  $("[data-toggle=collapse]").each(function() {
    var e = $(this);
    var target = e.attr('data-target');
    var startCollapsed = $(target).hasClass('collapse')
    if (startCollapsed) {
      $(target).toggle();
    }
    e.click(function() {
      $(target).toggle();
    });
  });
  $("[data-toggle=collapseAll]").each(function() {
    var targets = [];
    $("[data-target]").each(function() {
      var target = $(this).attr('data-target')
      targets.push($(target));
    });
    var e = $(this);
    e.click(function() {
      for (var i = 0; i < targets.length; i++) {
        targets[i].hide();
      }
    });
  });
  $("[data-toggle=openAll]").each(function() {
    var targets = [];
    $("[data-target]").each(function() {
      var target = $(this).attr('data-target')
      targets.push($(target));
    });
    var e = $(this);
    e.click(function() {
      for (var i = 0; i < targets.length; i++) {
        targets[i].show();
      }
    });
  });


  var fnLogProcessing = function (sSource, aoData, fnCallback) {
    var paging = this.fnPagingInfo();
    aoData.push( {"name": "page", "value": paging.iPage} );
    aoData.push( {"name": "size", "value": paging.iLength} );
    var sortDir = "DESC";
    for (var i = 0; i < aoData.length; i++) {
      if (aoData[i].name == "sSortDir_0") {
        sortDir = aoData[i].value;
        break;
      }
    }
    aoData.push( {"name": "sort", "value": sortDir} );
    $.ajax( {
      "dataType": 'json',
      "type": "GET",
      "url": sSource,
      "data": aoData,
      "success": function(json) {
        json.iTotalRecords = json.data.Pagination.TotalResults;
        json.iTotalDisplayRecords = json.iTotalRecords;
        fnCallback(json);
      }
    });
  };

  var oTable = $('table.log-data[data-source]').each(function() {
    var e = $(this);
    var src = e.attr('data-source');
    var errors = ["EMERGENCY", "ALERT", "CRITICAL"];
    var warnings = ["ERROR", "WARNING"];
    var notices = ["NOTICE"];
    var success = ["INFORMATIONAL"];

    var getLabelSpan = function(msg, label) {
      return '<span class="label ' + label + '">' + msg + '</span>';
    };
    e.dataTable({
      "bProcessing": true,
      "bServerSide": true,
      "bFilter": false,
      "sAjaxSource": src,
      "aaSorting": [[0, "desc"]],
      "sPaginationType": "bootstrap",
      "sDom": "<'row'<'span7'l><'span7'f>r>t<'row'<'span7'i><'span7'p>>",
      "iDisplayLength": 10,
      "fnServerData": fnLogProcessing,
      "sAjaxDataProp": "data.Data",
      "aoColumns": [
        { "mDataProp": "CREATED" },
        { "mDataProp": "SOURCE", "bSortable": false },
        { "mDataProp": "TYPE", "bSortable": false },
        { "mDataProp": "MESSAGE", "bSortable": false }
      ],
      "fnRowCallback": function( nRow, aData, iDisplayIndex ) {
        var dtype = aData.TYPE;
        if ($.inArray(dtype, errors) > -1) {
          $('td:eq(2)', nRow).html(getLabelSpan(dtype, "important"));
        } else if ($.inArray(dtype, warnings) > -1) {
          $('td:eq(2)', nRow).html(getLabelSpan(dtype, "warning"));
        } else if ($.inArray(dtype, notices) > -1) {
          $('td:eq(2)', nRow).html(getLabelSpan(dtype, "notice"));
        } else if ($.inArray(dtype, success) > -1) {
          $('td:eq(2)', nRow).html(getLabelSpan(dtype, "success"));
        } else {
          $('td:eq(2)', nRow).html(getLabelSpan(dtype, ""));
        }
        return nRow;
      }
    });
  });

})
