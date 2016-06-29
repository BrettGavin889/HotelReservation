angular.module('CrudApp', [])
.service('stateService', function State() {
	//service to share state between controllers
	var state = {};
	return {
		state: state,
	};
})
.config(['$routeProvider', function ($routeProvider) {
    //link urls to html files and controllers
	$routeProvider.
    	when('/', {templateUrl: '/tpl/authIndex.html', controller: MainCtrl}).
    	when('/search-room', {templateUrl: '/tpl/lists.html', controller: RoomCtrl}).
    	when('/signup', {templateUrl: '/signup.html', controller: SignupCtrl}).
    	when('/login', {templateUrl: '/loginpage.html', controller: LoginCtrl}).
        //TODO rename this control, and change the url for it
    	when('/edit/:id', {templateUrl: '/tpl/add-new.html', controller: EditCtrl}).
        when('/bookings/:id', {templateUrl: '/bookings.html', controller: BookingsCtrl}).
        otherwise({redirectTo: '/'});
}]);

//control for the main page
function MainCtrl($scope, $http, $location, stateService) {
    //reload the state variable
	$scope.state = stateService.state;
    
    if($scope.state.loggedIn) {
    	//if the user is logged in, reload the users info in case it changed
    	$http.get('/api/user/' + $scope.state.user.username).success(function (data) {
        	$scope.state.user = data;
        	
        	//if the user has prefences, fill out the form for them
        	//Note: if they are filling out the form already we don't want to overwrite what they've done
        	if($scope.state.room == null && $scope.state.user.room != null) {
        		$scope.state.room = $scope.state.user.room;
        	}
    	});
    }
    
    //handle when the user searches for a room
	$scope.search_room = function (room, RoomSearchForm) {
		//save the users selections in the browser
		$scope.state.room = room;
		$scope.state.viewAll = false;
		
    	$http.post('/api/rooms', room).success(function () {
        	//forward the user to the page containing the list of rooms
    		$scope.activePath = $location.path('/search-room');
        });
    };
    
    //handle when the user wants to see all rooms
    $scope.view_all = function () {
    	//indicate the user clicked viewAll then go to the results page
    	$scope.state.viewAll = true;
    	$scope.activePath = $location.path('/search-room');
    };
    
    //handle when the users saves their preferences
    $scope.save = function (room, RoomSearchForm) {
    	$http.put('/api/saveRoom/' + $scope.state.user.username, room).success(function (data) {
    		$scope.state.user = data;
    		alert("Your preferences have been saved");
        });
    };
    
    //handle when the user logs out
    $scope.logout = function (room, RoomSearchForm) {
		//reset all state about the user
    	$scope.loggedIn = false;
		$scope.state.loggedIn = false;
		$scope.state.user = null;
		$scope.state.room = null;
		$http.put('/logout').success(function () {
    		alert("You have logged out");
        });
    };
    
    //handle when the user adds money to their account
    $scope.money = function (room, RoomSearchForm) {
    	//prompt the user to see how much they want to add
		var amount = prompt("how much do you want to add?", 10);
		if(amount != null) {
			$http.put('/api/money/' + amount, $scope.state.user).success(function (data) {
	    		$scope.state.user = data;
	    		alert("Money has been added");
	        }).error(function () {
		        alert('Unable to add funds');
		    });
		}
    };
}

//Control for the page displaying bookings
function BookingsCtrl($scope, $http, $location, $routeParams, stateService) {
    //load the state variable
	$scope.state = stateService.state;
	var id = $routeParams.id;

	//get the list of bookings from the server
    $http.get('/api/bookings/' + id).success(function (data) {
    	$scope.bookings = data;
    });
}

//Control for the page displaying the list of rooms
function RoomCtrl($scope, $http, stateService) {
	//load the state variable
	$scope.state = stateService.state;
	
	//variables indicating how to sort the rooms initially
	$scope.orderByField = 'roomNumber';
	$scope.reverseSort = false;
	
	if($scope.state.viewAll) {
		//get the list of all rooms from the server
		$http.get('/api/allRooms').success(function (data) {
	    	$scope.rooms = data;
	    });
	} else {
		//get the list of rooms based on the users search
		$http.get('/api/rooms').success(function (data) {
	    	$scope.rooms = data;
	    });
	}
}

//handler for the signup page
function SignupCtrl($scope, $http, $location) {
	$scope.sign_up = function (user, AddNewForm) {
		//TODO want to check if the passwords match
		$http.post('/api/signup', user).success(function () {
			$scope.activePath = $location.path('/');
		}).error(function () {
	        alert('Unable to create account');
	    });
	};
}

//handler for the login page
function LoginCtrl($scope, $http, $location, stateService) {
	//reload the state variable
	$scope.state = stateService.state;
	
	//handle the login
	$scope.login = function() {
	    if ($scope.username.trim() != '' && $scope.password.trim() != '') {
	      $http.post(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/login', {username: $scope.username, password: $scope.password}).success(function(data, status) {
	        //load the users data
	    	$scope.loggedIn = true;
	        $scope.state.user = data;
	        $scope.state.loggedIn = true;
	        $scope.activePath = $location.path('/');
	      }).error(function () {
	        alert('invalid login');
	      });
	    }
	  };
}

//handler for the book room page
function EditCtrl($scope, $http, $location, $routeParams, stateService) {
	//load the state variable
	$scope.state = stateService.state;
	//create a user variable
	$scope.user = {};
	//by default the user doesnt pay from their account
	$scope.user.paymentChecked = false;
	
	//update the initial values if the user is logged in
	if($scope.state.user != null) {
		//fill in the text boxes
		$scope.user.firstName = $scope.state.user.firstName;
		$scope.user.lastName = $scope.state.user.lastName;
		$scope.user.paymentInfo = $scope.state.user.paymentInfo;
		$scope.user.email = $scope.state.user.email;
	
		//check the check box if appropriate
		if($scope.state.user.accountBalance != null) {
			$scope.user.paymentChecked = true;
		}
	}
	
    var id = $routeParams.id;
    $scope.activePath = null;

    //handle booking the room
    $scope.book_room = function (user) {
    	//save the users info if it has not been saved yet
    	if($scope.state.loggedIn && $scope.user != $scope.state.user) {
    		//the user has not defined their name and payment info yet
    		$http.put('/api/saveName/' + $scope.state.user.username, user).success(function (data) {
                $scope.state.user = data;
            });
    	}
    	
    	//send the information to the server to book the room
        $http.put('/api/book_room/' + id, user).success(function (data) {
            $scope.user = data;
            $scope.activePath = $location.path('/');
        }).
        error(function(data, status, headers, config) {
            console.log("error");
            alert("Couldn't book room: " + data);
            // custom handle error
        });
    };
}