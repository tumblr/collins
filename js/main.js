$(document).ready(function() {

  var $window = $(window)

  $('.sidenav:not(.huge-nav)').affix({
    offset: {
      top: function() { return $window.width() <= 980 ? 290 : 210 },
      bottom: 270
    }
  })

  // turn off auto cycling
  $('.carousel').carousel({
    interval: false
  })

});
