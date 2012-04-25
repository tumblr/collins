// Code related to refreshing visual/UI elements after some action has taken place.

$(document).ready(function() {
  // Triggers a refresh on the specified element at the specified interval
  // example: <button data-interval-refresh="30;#mySelector">Do Something</button>
  // This will trigger a refresh every 30 seconds. Note that the element must have a
  // binding to the refresh event. Data tables have this automatically.
  $("button[data-interval-refresh]").each(function() {
    var e = $(this);
    var spec = e.attr('data-interval-refresh').split(';');
    var intervalTimeout = 5000;
    var name = null;
    if (spec.length == 1) {
      name = spec[0];
    } else {
      intervalTimeout = parseInt(spec[0]) * 1000;
      name = spec[1];
    }
    if (intervalTimeout < 5000) {
      intervalTimeout = 5000;
    }
    var refreshElement = $(name);
    function resetInterval(el, i) {
      if (i !== null) {
        clearInterval(i);
      }
      el.attr('running', 'false');
      return null;
    };
    var label = e.html();
    var interval = null;
    e.click(function() {
      if (e.attr('running') == 'true') {
        interval = resetInterval(e, interval);
        e.html(label + " Not Running");
      } else {
        interval = resetInterval(e, interval);
        var count = 0;
        function doRefresh() {
          $(refreshElement).trigger('refresh');
          count += 1;
          e.html(label + " Running (" + count + ")");
        };
        e.attr('running', 'true');
        doRefresh();
        interval = setInterval(doRefresh, intervalTimeout);
      }
    });
  });

  var refresher = function(selector) {
    if (selector) {
      $(selector).each(function() { $(this).trigger('refresh') });
    }
  }

  // hack so data-refresh can be "window"
  $("body").append("<div class='hide window' id='window'></div>");
  $('#window,.window').bind('refresh', function() {
    window.location.reload();
  });

});

