package com.example.gvdmovie.view.details

import android.os.Bundle
import android.os.SystemClock.sleep
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.gvdmovie.R
import com.example.gvdmovie.databinding.DetailFragmentBinding
import com.example.gvdmovie.model.Movie
import com.example.gvdmovie.room.NoteEntity
import com.example.gvdmovie.utils.DEFAULT_LANGUAGE
import com.example.gvdmovie.utils.showSnackBar
import com.example.gvdmovie.viewmodel.AppState
import com.example.gvdmovie.viewmodel.DetailViewModel
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.detail_fragment.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

const val POSTER_WIDTH = "w500"

class DetailFragment : Fragment(), CoroutineScope {

    private var _binding: DetailFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var movieBundle: Movie

    private val viewModel: DetailViewModel by lazy { ViewModelProvider(this).get(DetailViewModel::class.java) }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DetailFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        movieBundle = arguments?.getParcelable(BUNDLE_EXTRA) ?: Movie()

        viewModel.detailsLiveData.observe(viewLifecycleOwner, Observer { renderData(it) })
        viewModel.getMovieFromRemoteSource(movieBundle.id.toString(), DEFAULT_LANGUAGE)
    }

    private fun renderData(appState: AppState) {
        when (appState) {
            is AppState.Success -> {
                mainView.visibility = View.VISIBLE
                binding.includedLoadingLayout.loadingLayout.visibility = View.GONE
                setMovie(appState.movieData[0])
            }
            is AppState.Loading -> {
                mainView.visibility = View.GONE
                binding.includedLoadingLayout.loadingLayout.visibility = View.VISIBLE
            }
            is AppState.Error -> {
                mainView.visibility = View.VISIBLE
                binding.includedLoadingLayout.loadingLayout.visibility = View.GONE
                mainView.showSnackBar(
                    getString(R.string.error),
                    getString(R.string.reload),
                    { viewModel.getMovieFromRemoteSource(movieBundle.id.toString(), DEFAULT_LANGUAGE) })
            }
        }
    }

    private fun setMovie(movie: Movie) {

        saveMovie(movie)

        with(binding) {
            message.text = getString(R.string.movie_details_info)

            movieNote.setText(getString(R.string.loading))

            movieTitle.text = movie.title
            movieOriginalTitle.text = movie.originalTitle
            movieReleaseDate.text = movie.releaseDate
            movieTagline.text = movie.tagline
            movieRuntime.text = movie.runtime

            Picasso
                .get()
                .load("https://image.tmdb.org/t/p/${POSTER_WIDTH}${movie.poster}")
                .into(movieImage)

            val notes = async {
//                sleep(1000)
                viewModel.getMovieNotes(movie)
            }

            launch {
                val notesList: List<NoteEntity> = notes.await()
                val note = if (notesList.isNotEmpty()) {
                    notesList.first().note
                } else {
                    ""
                }
                movieNote.setText(note)
            }

            saveButton.setOnClickListener {
                saveNote(movie)
            }
        }
    }

    private fun saveMovie(movie: Movie) {
        viewModel.saveMovieToDB(movie)
    }

    private fun saveNote(movie: Movie) {

        val note: String = binding.movieNote.text.toString() //"Заметка к фильму"

        if (note.isNotBlank())
            viewModel.saveNoteToDB(NoteEntity(movie.id.toString(), note))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        coroutineContext.cancelChildren()
    }

    companion object {
        const val BUNDLE_EXTRA = "movie"

        fun newInstance(bundle: Bundle): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = bundle
            return fragment
        }
    }
}
