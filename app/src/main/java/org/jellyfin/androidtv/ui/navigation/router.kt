@file:OptIn(ExperimentalSharedTransitionApi::class)

package org.jellyfin.androidtv.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

typealias RouteParameters = Map<String, String>
typealias RouteComposable = @Composable ((context: RouteContext) -> Unit)

data class RouteContext(
	val route: String,
	val parameters: RouteParameters,
)

class Router(
	val routes: Map<String, RouteComposable>,
	val backStack: SnapshotStateList<RouteContext>,
) {
	// Route resolving

	fun resolve(route: String): RouteComposable? = routes[route]
	fun verifyRoute(route: String, parameters: RouteParameters = emptyMap()) = require(resolve(route) != null) { "Invalid route $route" }

	// Route manipulation

	fun push(route: String, parameters: RouteParameters = emptyMap()) {
		verifyRoute(route, parameters)

		val context = RouteContext(route, parameters)
		backStack.add(context)
	}

	fun replace(route: String, parameters: RouteParameters = emptyMap()) {
		verifyRoute(route, parameters)

		val context = RouteContext(route, parameters)
		backStack.removeLastOrNull()
		backStack.add(context)
	}

	fun back() {
		backStack.removeLastOrNull()
	}
}

val LocalRouter = compositionLocalOf<Router> { error("No router provided") }
val LocalRouterTransitionScope = compositionLocalOf<SharedTransitionScope> { error("No router transition scope provided") }

@Composable
fun ProvideRouter(
	routes: Map<String, RouteComposable>,
	defaultRoute: String,
	defaultRouteParameters: RouteParameters = emptyMap(),
	content: @Composable () -> Unit,
) {
	val backStack = remember { mutableStateListOf(RouteContext(defaultRoute, defaultRouteParameters)) }
	val router = remember(routes, backStack) {
		Router(
			routes = routes,
			backStack = backStack,
		)
	}

	CompositionLocalProvider(
		LocalRouter provides router,
		content = content
	)
}

@Composable
fun RouterContent(
	router: Router = LocalRouter.current,
	fallbackRoute: String = "/",
	transitionSpec: AnimatedContentTransitionScope<RouteContext>.() -> ContentTransform = { fadeIn() togetherWith fadeOut() },
	popTransitionSpec: AnimatedContentTransitionScope<RouteContext>.() -> ContentTransform = transitionSpec,
) {
	val backStack = router.backStack
	val currentEntry = backStack.lastOrNull() ?: RouteContext(fallbackRoute, emptyMap())
	val saveableStateHolder = rememberSaveableStateHolder()
	var lastBackStackSize by remember { mutableStateOf(backStack.size) }
	val isPop = backStack.size < lastBackStackSize

	LaunchedEffect(backStack.size) {
		lastBackStackSize = backStack.size
	}

	SharedTransitionLayout {
		CompositionLocalProvider(LocalRouterTransitionScope provides this@SharedTransitionLayout) {
			AnimatedContent(
				targetState = currentEntry,
				transitionSpec = { if (isPop) popTransitionSpec() else transitionSpec() },
				label = "RouterContent"
			) { entry ->
				val composable = router.resolve(entry.route)
				val fallbackComposable = if (composable == null) {
					router.resolve(fallbackRoute)
						?: error("Unknown route ${entry.route}, fallback $fallbackRoute is invalid")
				} else {
					null
				}
				val context = if (composable == null) entry.copy(route = fallbackRoute) else entry
				val stateKey = buildStateKey(context)

				saveableStateHolder.SaveableStateProvider(stateKey) {
					(composable ?: fallbackComposable)?.invoke(context)
				}
			}
		}
	}
}

private fun buildStateKey(context: RouteContext): String {
	if (context.parameters.isEmpty()) return context.route
	val encoded = context.parameters.entries
		.sortedBy { it.key }
		.joinToString("&") { "${it.key}=${it.value}" }
	return "${context.route}?$encoded"
}
