angular.module('CrudApp', [])
.service('stateService', function State() {
	var state = {};
	return {
		state: state,
	};
})
.config(['$routeProvider', function ($routeProvider) {
    $routeProvider.
    	when('/', {templateUrl: '/tpl/authIndex.html', controller: ListCtrl}).
    	when('/search-room', {templateUrl: '/tpl/lists.html', controller: RoomCtrl}).
    	when('/signup', {templateUrl: '/signup.html', controller: SignupCtrl}).
    	when('/login', {templateUrl: '/loginpage.html', controller: LoginCtrl}).
    	when('/add-user', {templateUrl: '/tpl/add-new.html', controller: AddCtrl}).
        when('/edit/:id', {templateUrl: '/tpl/add-new.html', controller: EditCtrl}).
        when('/bookings/:id', {templateUrl: '/bookings.html', controller: BookingsCtrl}).
        otherwise({redirectTo: '/'});
}]);

function ListCtrl($scope, $http, $location, stateService) {
    $scope.state = stateService.state;
    
    if($scope.state.loggedIn) {
    	$http.get('/api/user/' + $scope.state.user.username).success(function (data) {
        	$scope.state.user = data;
        });
    }
    
    if($scope.state.user != null && $scope.state.user.firstName != null) {
    	$scope.room = {};
    	$scope.room.name = $scope.state.user.firstName + " " + $scope.state.user.lastName;
    }
	$scope.search_room = function (room, RoomSearchForm) {
			$scope.state.room = room;
    		$http.post('/api/rooms', room).success(function () {
        		$scope.activePath = $location.path('/search-room');
            });
    };
    $scope.save = function (room, RoomSearchForm) {
    	$scope.state.room = room;
    	$http.put('/api/userRoom', room).success(function (data) {
    		$scope.state.user = data;
    		alert("Your preferences have been saved");
        });
    };
    $scope.logout = function (room, RoomSearchForm) {
		$scope.loggedIn = false;
		$scope.state.loggedIn = false;
		$scope.state.user = null;
		$scope.state.room = null;
		$http.put('/logout').success(function () {
    		alert("You have logged out");
        });
    };
    $scope.money = function (room, RoomSearchForm) {
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
//    $scope.bookings = function () {
//    	$http.get('/api/bookings/user').success(function (data) {
//        	$scope.bookings = data;
//        });
//    };
}

function BookingsCtrl($scope, $http, $location, $routeParams, stateService) {
    $scope.state = stateService.state;
    var id = $routeParams.id;

    $http.get('/api/bookings/' + id).success(function (data) {
    	$scope.bookings = data;
    });
}

function RoomCtrl($scope, $http) {
	$http.get('/api/rooms').success(function (data) {
    	$scope.rooms = data;
    });
	
	$scope.book_room = function (room) {
		$scope.state.roomNumber = room;
		$scope.activePath = $location.path('/')
	};
}

function SignupCtrl($scope, $http, $location) {
	$scope.sign_up = function (user, AddNewForm) {
		$http.post('/api/signup', user).success(function () {
			$scope.activePath = $location.path('/');
		});
	};
}

function LoginCtrl($scope, $http, $location, stateService) {
	$scope.state = stateService.state;
	$scope.login = function() {
	    if ($scope.username.trim() != '' && $scope.password.trim() != '') {
	      $http.post(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/login', {username: $scope.username, password: $scope.password}).success(function(data, status) {
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

function AddCtrl($scope, $http, $location) {
    $scope.master = {};
    $scope.activePath = null;

    $scope.add_new = function (user, AddNewForm) {

        $http.post('/api/users', user).success(function () {
            $scope.reset();
            $scope.activePath = $location.path('/');
        });
        
        $scope.reset = function () {
            $scope.user = angular.copy($scope.master);
        };

        $scope.reset();

    };
    $scope.add_new = function (user, AddNewForm) {
    	$http.post('/api/bookRoom', user).success(function () {
    		$scope.reset();
    		$scope.activePath = $location.path('/');
    	});
    	
    	$scope.reset = function () {
    		$scope.user = angular.copy($scope.master);
    	};
    	
    	$scope.reset();
    };
}

function disable() {
	var cb = document.getElementById('payfromaccount').checked;
	document.getElementById('')
}
function EditCtrl($scope, $http, $location, $routeParams, stateService) {
	$scope.state = stateService.state;
	
	$scope.user = {};
	$scope.user.paymentChecked = false;
	
	//update the initial values if the user is logged in
	if($scope.state.user != null) {
		//fill in the text boxes
		$scope.user.firstName = $scope.state.user.firstName;
		$scope.user.lastName = $scope.state.user.lastName;
		$scope.user.paymentInfo = $scope.state.user.paymentInfo;
		$scope.user.email = $scope.state.user.email;
	
		//check the check box
		if($scope.state.user.accountBalance != null) {
			$scope.user.paymentChecked = true;
		}
	}
	
    var id = $routeParams.id;
    $scope.activePath = null;

    //$http.get('/api/users/' + id).success(function (data) {
    //    $scope.user = data;
    //});

    $scope.book_room = function (user) {
    	if($scope.state.loggedIn && $scope.user != $scope.state.user) {
    		//the user has not defined their name and payment info yet
    		$http.put('/api/saveName/' + $scope.state.user.username, user).success(function (data) {
                $scope.state.user = data;
            });
    	}
    	
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
    
    $scope.disable = function() {
    	var cb = document.getElementById('payfromaccount').checked;
    	document.getElementById('paymentinfo').disabled = cb;
    }
    
    $scope.delete = function (user) {
        var deleteUser = confirm('Are you absolutely sure you want to delete ?');
        if (deleteUser) {
            $http.delete('/api/users/' + id)
                .success(function(data, status, headers, config) {
                    $scope.activePath = $location.path('/');
                }).
                error(function(data, status, headers, config) {
                    console.log("error");
                    // custom handle error
                });
        }
    };
}